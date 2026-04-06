package io.github.raphonzius.lvc.live.application.service;

import io.github.raphonzius.lvc.live.domain.flooding.FloodingPoint;
import io.github.raphonzius.lvc.live.domain.flooding.FloodingPort;
import io.github.raphonzius.lvc.live.domain.streaming.FloodingStreamingPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CgespService} orchestration.
 */
@ExtendWith(MockitoExtension.class)
class CgespServiceTest {

    private static final ZoneId SAO_PAULO_TZ = ZoneId.of("America/Sao_Paulo");

    @Mock
    private FloodingPort floodingPort;

    @Mock
    private FloodingStreamingPort floodingStreamingPort;

    @InjectMocks
    private CgespService service;

    // -------------------------------------------------------------------------
    // fetchAndStreamFloodings
    // -------------------------------------------------------------------------

    @Test
    void fetchAndStreamFloodings_usesTodayInSpTimezone() {
        LocalDate before = LocalDate.now(SAO_PAULO_TZ);
        when(floodingPort.fetchFloodings(any())).thenReturn(List.of());

        service.fetchAndStreamFloodings();

        LocalDate after = LocalDate.now(SAO_PAULO_TZ);
        ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(floodingPort).fetchFloodings(dateCaptor.capture());
        assertThat(dateCaptor.getValue()).isBetween(before, after);
    }

    @Test
    void fetchAndStreamFloodings_publishesReturnedPoints() {
        LocalDate today = LocalDate.now(SAO_PAULO_TZ);
        List<FloodingPoint.FloodData> floodings = List.of(
                new FloodingPoint.FloodData(
                        today, "Zona Leste", "São Mateus",
                        FloodingPoint.FloodStatus.ATIVO_INTRANSITAVEL,
                        LocalTime.of(17, 52), LocalTime.of(23, 59),
                        "AV JOSE ALENCAR GOMES DA SILVA", "CENTRO/BAIRRO", "AV SAPOPEMBA"
                )
        );
        when(floodingPort.fetchFloodings(any())).thenReturn(floodings);

        service.fetchAndStreamFloodings();

        verify(floodingStreamingPort).publishFloodings(any(LocalDate.class), eq(floodings));
    }

    @Test
    void fetchAndStreamFloodings_portReturnsEmpty_stillPublishes() {
        when(floodingPort.fetchFloodings(any())).thenReturn(List.of());

        service.fetchAndStreamFloodings();

        verify(floodingStreamingPort).publishFloodings(any(LocalDate.class), eq(List.of()));
    }

    // -------------------------------------------------------------------------
    // fetchFloodingsByDate
    // -------------------------------------------------------------------------

    @Test
    void fetchFloodingsByDate_delegatesToPort() {
        LocalDate date = LocalDate.of(2026, 4, 1);
        List<FloodingPoint.FloodData> expected = List.of();
        when(floodingPort.fetchFloodings(date)).thenReturn(expected);

        List<FloodingPoint.FloodData> result = service.fetchFloodingsByDate(date);

        assertThat(result).isSameAs(expected);
        verify(floodingPort).fetchFloodings(date);
        verifyNoInteractions(floodingStreamingPort);
    }
}
