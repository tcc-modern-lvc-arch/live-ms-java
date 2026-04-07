package io.github.raphonzius.lvc.live.infrastructure.input.external.olhovivo;

import io.github.raphonzius.lvc.live.domain.bus.BusLine;
import io.github.raphonzius.lvc.live.domain.bus.BusVehicle;
import io.github.raphonzius.lvc.live.domain.bus.VehiclePosition;
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

    private OlhoVivoAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new OlhoVivoAdapter(feignClient);
    }

    // -------------------------------------------------------------------------
    // searchLines
    // -------------------------------------------------------------------------

    @Test
    void searchLines_delegatesToFeignClient() {
        List<BusLine.LineData> expected = List.of(lineData(1001, 10), lineData(1002, 10));
        when(feignClient.searchLines("178L")).thenReturn(expected);

        List<BusLine.LineData> result = adapter.searchLines("178L");

        assertThat(result).isSameAs(expected);
        verify(feignClient).searchLines("178L");
    }

    @Test
    void searchLines_returnsEmptyListWhenFeignReturnsEmpty() {
        when(feignClient.searchLines("UNKNOWN")).thenReturn(List.of());

        List<BusLine.LineData> result = adapter.searchLines("UNKNOWN");

        assertThat(result).isEmpty();
    }

    @Test
    void searchLines_fallback_returnsEmptyList() {
        List<BusLine.LineData> result =
                adapter.searchLinesFallback("178L", new RuntimeException("circuit open"));

        assertThat(result).isEmpty();
    }

    @Test
    void searchLines_fallback_returnsEmptyList_onAnyException() {
        List<BusLine.LineData> result =
                adapter.searchLinesFallback("5000", new IllegalStateException("timeout"));

        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // positionsByLine
    // -------------------------------------------------------------------------

    @Test
    void positionsByLine_delegatesToFeignClient() {
        VehiclePosition.PositionResponse expected = positionResponse(List.of(vehicleData(42)));
        when(feignClient.positionsByLine(1001)).thenReturn(expected);

        VehiclePosition.PositionResponse result = adapter.positionsByLine(1001);

        assertThat(result).isSameAs(expected);
        verify(feignClient).positionsByLine(1001);
    }

    @Test
    void positionsByLine_returnsResponseWithVehicles() {
        List<BusVehicle.VehicleData> vehicles = List.of(vehicleData(10), vehicleData(20));
        VehiclePosition.PositionResponse response = positionResponse(vehicles);
        when(feignClient.positionsByLine(999)).thenReturn(response);

        VehiclePosition.PositionResponse result = adapter.positionsByLine(999);

        assertThat(result.vs()).hasSize(2);
        assertThat(result.hr()).isEqualTo("18:00");
    }

    @Test
    void positionsByLine_fallback_returnsEmptyResponse() {
        VehiclePosition.PositionResponse result =
                adapter.positionsByLineFallback(1001, new RuntimeException("circuit open"));

        assertThat(result.hr()).isEqualTo("--:--");
        assertThat(result.vs()).isEmpty();
        assertThat(result.l()).isEmpty();
    }

    @Test
    void positionsByLine_fallback_returnsEmptyResponse_onAnyException() {
        VehiclePosition.PositionResponse result =
                adapter.positionsByLineFallback(999, new IllegalStateException("timeout"));

        assertThat(result).isEqualTo(VehiclePosition.PositionResponse.empty());
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
