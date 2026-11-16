package io.github.raphonzius.lvc.live.infrastructure.input.external.olhovivo;

import io.github.raphonzius.lvc.live.domain.FetchResult;
import io.github.raphonzius.lvc.live.domain.bus.BusLine;
import io.github.raphonzius.lvc.live.domain.bus.BusVehicle;
import io.github.raphonzius.lvc.live.domain.bus.VehiclePosition;
import io.github.raphonzius.lvc.live.infrastructure.input.external.UpstreamTelemetry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OlhoVivoAdapter}.
 *
 * <p>{@code @CircuitBreaker} AOP is not active without a Spring context,
 * so both methods run their real bodies directly.</p>
 */
@ExtendWith(MockitoExtension.class)
class OlhoVivoAdapterTest {

    @Mock
    private OlhoVivoFeignClient feignClient;

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private OlhoVivoAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new OlhoVivoAdapter(feignClient,
                new UpstreamTelemetry(registry));
    }

    // -------------------------------------------------------------------------
    // searchLines
    // -------------------------------------------------------------------------

    @Test
    void searchLines_delegatesToFeignClient() {
        List<BusLine.LineData> expected = List.of(lineData(1001, 10), lineData(1002, 10));
        when(feignClient.searchLines("178L")).thenReturn(expected);

        var result = adapter.searchLines("178L");

        assertThat(result).isEqualTo(FetchResult.of(expected));
        verify(feignClient).searchLines("178L");
    }

    @Test
    void searchLines_returnsEmptyFetchedListWhenFeignReturnsEmpty() {
        when(feignClient.searchLines("UNKNOWN")).thenReturn(List.of());

        assertThat(adapter.searchLines("UNKNOWN")).isEqualTo(FetchResult.of(List.<BusLine.LineData>of()));
    }

    @Test
    void searchLines_fallback_isUnavailableNotEmpty_andCounted() {
        var result = adapter.searchLinesFallback("178L", new IllegalStateException("timeout"));

        assertThat(result).isEqualTo(FetchResult.unavailable("IllegalStateException"));
        assertThat(registry.get("lvc.upstream.fallback")
                .tags("api", "olhovivo", "reason", "IllegalStateException").counter().count()).isEqualTo(1.0);
    }

    // -------------------------------------------------------------------------
    // positionsByLine
    // -------------------------------------------------------------------------

    @Test
    void positionsByLine_delegatesToFeignClient() {
        VehiclePosition.PositionResponse expected = positionResponse(List.of(vehicleData(42)));
        when(feignClient.positionsByLine(1001)).thenReturn(expected);

        var result = adapter.positionsByLine(1001);

        assertThat(result).isEqualTo(FetchResult.of(expected));
        verify(feignClient).positionsByLine(1001);
    }

    @Test
    void positionsByLine_returnsResponseWithVehicles() {
        List<BusVehicle.VehicleData> vehicles = List.of(vehicleData(10), vehicleData(20));
        when(feignClient.positionsByLine(999)).thenReturn(positionResponse(vehicles));

        var result = adapter.positionsByLine(999).orElse(null);

        assertThat(result.vs()).hasSize(2);
        assertThat(result.hr()).isEqualTo("18:00");
    }

    @Test
    void positionsByLine_fallback_isUnavailableNotAnEmptyResponse() {
        var result = adapter.positionsByLineFallback(1001, new RuntimeException("circuit open"));

        assertThat(result).isEqualTo(FetchResult.unavailable("RuntimeException"));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static BusLine.LineData lineData(int cl, int tl) {
        return new BusLine.LineData(cl, false, "178L", 1, tl, "TERM PRINCIPAL", "TERM SECUNDARIO");
    }

    private static BusVehicle.VehicleData vehicleData(int prefix) {
        return new BusVehicle.VehicleData(prefix, true, "2026-04-06T18:00:00Z", -23.5, -46.5);
    }

    private static VehiclePosition.PositionResponse positionResponse(List<BusVehicle.VehicleData> vehicles) {
        return new VehiclePosition.PositionResponse("18:00", List.of(), vehicles);
    }
}
