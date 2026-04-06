package io.github.raphonzius.lvc.live.infrastructure.input.external.cgesp;

import io.github.raphonzius.lvc.live.domain.flooding.FloodingPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CgespAdapter} HTML parsing logic.
 *
 * <p>{@code @CircuitBreaker} AOP does not activate without a Spring context,
 * so {@code fetchFloodings} runs the real method body directly — ideal for parsing tests.</p>
 */
@ExtendWith(MockitoExtension.class)
class CgespAdapterTest {

    private static final LocalDate HISTORICAL_DATE = LocalDate.of(2026, 4, 1);
    private static final ZoneId SAO_PAULO_TZ = ZoneId.of("America/Sao_Paulo");

    @Mock
    private CgespFeignClient feignClient;

    private CgespAdapter adapter;
    private String sampleHtml;

    @BeforeEach
    void setUp() throws IOException, URISyntaxException {
        adapter = new CgespAdapter(feignClient);
        sampleHtml = Files.readString(
                Path.of(Objects.requireNonNull(
                        getClass().getClassLoader().getResource("cgesp/alagamentos_sample.html")
                ).toURI())
        );
    }

    // -------------------------------------------------------------------------
    // Full parse — historical date (deterministic end-time inference)
    // -------------------------------------------------------------------------

    @Test
    void fetchFloodings_parsesAllFourPoints() {
        when(feignClient.fetchFloodingsHtml("01/04/2026", "Buscar")).thenReturn(sampleHtml);

        List<FloodingPoint.FloodData> result = adapter.fetchFloodings(HISTORICAL_DATE);

        assertThat(result).hasSize(4);
    }

    @Test
    void fetchFloodings_delegatesDateFormattingToClient() {
        when(feignClient.fetchFloodingsHtml(eq("01/04/2026"), eq("Buscar"))).thenReturn(sampleHtml);

        adapter.fetchFloodings(HISTORICAL_DATE);

        verify(feignClient).fetchFloodingsHtml("01/04/2026", "Buscar");
    }

    // -------------------------------------------------------------------------
    // Zone + neighborhood
    // -------------------------------------------------------------------------

    @Test
    void fetchFloodings_assignsCorrectZones() {
        when(feignClient.fetchFloodingsHtml("01/04/2026", "Buscar")).thenReturn(sampleHtml);

        List<FloodingPoint.FloodData> result = adapter.fetchFloodings(HISTORICAL_DATE);

        assertThat(result.get(0).zone()).isEqualTo("Zona Leste");
        assertThat(result.get(1).zone()).isEqualTo("Zona Sul");
        assertThat(result.get(2).zone()).isEqualTo("Zona Sul");
        assertThat(result.get(3).zone()).isEqualTo("Zona Norte");
    }

    @Test
    void fetchFloodings_assignsCorrectNeighborhoods() {
        when(feignClient.fetchFloodingsHtml("01/04/2026", "Buscar")).thenReturn(sampleHtml);

        List<FloodingPoint.FloodData> result = adapter.fetchFloodings(HISTORICAL_DATE);

        assertThat(result.get(0).neighborhood()).isEqualTo("São Mateus");
        assertThat(result.get(1).neighborhood()).isEqualTo("Vila Prudente");
        assertThat(result.get(2).neighborhood()).isEqualTo("Vila Prudente");
        assertThat(result.get(3).neighborhood()).isEqualTo("Santana");
    }

    // -------------------------------------------------------------------------
    // Status classification
    // -------------------------------------------------------------------------

    @Test
    void fetchFloodings_parsesAllFourStatusClassifications() {
        when(feignClient.fetchFloodingsHtml("01/04/2026", "Buscar")).thenReturn(sampleHtml);

        List<FloodingPoint.FloodData> result = adapter.fetchFloodings(HISTORICAL_DATE);

        assertThat(result.get(0).status()).isEqualTo(FloodingPoint.FloodStatus.ATIVO_INTRANSITAVEL);
        assertThat(result.get(1).status()).isEqualTo(FloodingPoint.FloodStatus.INATIVO_INTRANSITAVEL);
        assertThat(result.get(2).status()).isEqualTo(FloodingPoint.FloodStatus.INATIVO_TRANSITAVEL);
        assertThat(result.get(3).status()).isEqualTo(FloodingPoint.FloodStatus.ATIVO_TRANSITAVEL);
    }

    // -------------------------------------------------------------------------
    // Time parsing
    // -------------------------------------------------------------------------

    @Test
    void fetchFloodings_parsesStartTime() {
        when(feignClient.fetchFloodingsHtml("01/04/2026", "Buscar")).thenReturn(sampleHtml);

        List<FloodingPoint.FloodData> result = adapter.fetchFloodings(HISTORICAL_DATE);

        assertThat(result.get(0).startTime()).isEqualTo(LocalTime.of(17, 52));
        assertThat(result.get(1).startTime()).isEqualTo(LocalTime.of(17, 50));
        assertThat(result.get(2).startTime()).isEqualTo(LocalTime.of(17, 50));
        assertThat(result.get(3).startTime()).isEqualTo(LocalTime.of(18, 10));
    }

    @Test
    void fetchFloodings_parsesExplicitEndTime() {
        when(feignClient.fetchFloodingsHtml("01/04/2026", "Buscar")).thenReturn(sampleHtml);

        List<FloodingPoint.FloodData> result = adapter.fetchFloodings(HISTORICAL_DATE);

        // points 1 and 2 have explicit end times
        assertThat(result.get(1).endTime()).isEqualTo(LocalTime.of(19, 0));
        assertThat(result.get(2).endTime()).isEqualTo(LocalTime.of(19, 8));
    }

    @Test
    void fetchFloodings_historicalDate_noEndTime_inferredAs2359() {
        when(feignClient.fetchFloodingsHtml("01/04/2026", "Buscar")).thenReturn(sampleHtml);

        List<FloodingPoint.FloodData> result = adapter.fetchFloodings(HISTORICAL_DATE);

        // points 0 and 3 have no end time — historical date → 23:59
        assertThat(result.get(0).endTime()).isEqualTo(LocalTime.of(23, 59));
        assertThat(result.get(3).endTime()).isEqualTo(LocalTime.of(23, 59));
    }

    @Test
    void fetchFloodings_today_noEndTime_inferredAsCurrentSpTime() {
        LocalDate today = LocalDate.now(SAO_PAULO_TZ);
        LocalTime before = LocalTime.now(SAO_PAULO_TZ);
        when(feignClient.fetchFloodingsHtml(
                today.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")), "Buscar"
        )).thenReturn(sampleHtml);

        List<FloodingPoint.FloodData> result = adapter.fetchFloodings(today);

        LocalTime after = LocalTime.now(SAO_PAULO_TZ);
        // point 0 has no end time — today → endTime ≈ now
        assertThat(result.get(0).endTime())
                .isNotNull()
                .isAfterOrEqualTo(before.withNano(0))
                .isBeforeOrEqualTo(after.plusSeconds(1));
    }

    // -------------------------------------------------------------------------
    // Street, direction, reference
    // -------------------------------------------------------------------------

    @Test
    void fetchFloodings_parsesStreet() {
        when(feignClient.fetchFloodingsHtml("01/04/2026", "Buscar")).thenReturn(sampleHtml);

        List<FloodingPoint.FloodData> result = adapter.fetchFloodings(HISTORICAL_DATE);

        assertThat(result.get(0).street()).isEqualTo("AV JOSE ALENCAR GOMES DA SILVA");
        assertThat(result.get(1).street()).isEqualTo("R ANTONIO DE ALMEIDA");
        assertThat(result.get(2).street()).isEqualTo("AV PROF LUIZ IGNACIO ANHAIA MELLO");
        assertThat(result.get(3).street()).isEqualTo("R VOLUNTARIOS DA PATRIA");
    }

    @Test
    void fetchFloodings_parsesDirection() {
        when(feignClient.fetchFloodingsHtml("01/04/2026", "Buscar")).thenReturn(sampleHtml);

        List<FloodingPoint.FloodData> result = adapter.fetchFloodings(HISTORICAL_DATE);

        assertThat(result.get(0).direction()).isEqualTo("CENTRO/BAIRRO");
        assertThat(result.get(1).direction()).isEqualTo("AMBOS");
        assertThat(result.get(2).direction()).isEqualTo("SAPOPEMBA/V PRUDENTE");
        assertThat(result.get(3).direction()).isEqualTo("BAIRRO/CENTRO");
    }

    @Test
    void fetchFloodings_parsesReference() {
        when(feignClient.fetchFloodingsHtml("01/04/2026", "Buscar")).thenReturn(sampleHtml);

        List<FloodingPoint.FloodData> result = adapter.fetchFloodings(HISTORICAL_DATE);

        assertThat(result.get(0).reference()).isEqualTo("AV SAPOPEMBA");
        assertThat(result.get(1).reference()).isEqualTo("R MANUEL DE AVITA");
        assertThat(result.get(2).reference()).isEqualTo("R DOMINGOS AFONSO");
        assertThat(result.get(3).reference()).isEqualTo("AV CRUZEIRO DO SUL");
    }

    // -------------------------------------------------------------------------
    // Edge cases
    // -------------------------------------------------------------------------

    @Test
    void fetchFloodings_noContentSection_returnsEmpty() {
        when(feignClient.fetchFloodingsHtml("01/04/2026", "Buscar"))
                .thenReturn("<html><body><p>Sem alagamentos</p></body></html>");

        List<FloodingPoint.FloodData> result = adapter.fetchFloodings(HISTORICAL_DATE);

        assertThat(result).isEmpty();
    }

    @Test
    void fetchFloodings_emptyHtml_returnsEmpty() {
        when(feignClient.fetchFloodingsHtml("01/04/2026", "Buscar")).thenReturn("");

        List<FloodingPoint.FloodData> result = adapter.fetchFloodings(HISTORICAL_DATE);

        assertThat(result).isEmpty();
    }

    @Test
    void fetchFloodings_setsDateOnAllPoints() {
        when(feignClient.fetchFloodingsHtml("01/04/2026", "Buscar")).thenReturn(sampleHtml);

        List<FloodingPoint.FloodData> result = adapter.fetchFloodings(HISTORICAL_DATE);

        assertThat(result).allMatch(p -> HISTORICAL_DATE.equals(p.date()));
    }

    // -------------------------------------------------------------------------
    // Fallback
    // -------------------------------------------------------------------------

    @Test
    void fallback_returnsEmptyList() {
        List<FloodingPoint.FloodData> result =
                adapter.fetchFloodingsFallback(HISTORICAL_DATE, new RuntimeException("circuit open"));

        assertThat(result).isEmpty();
    }
}
