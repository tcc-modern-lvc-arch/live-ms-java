package io.github.raphonzius.lvc.live.infrastructure.input.external.ais;

import io.github.raphonzius.lvc.live.domain.vessel.Vessel;
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
 * Unit tests for {@link AisAdapter}.
 *
 * <p>{@code @CircuitBreaker} AOP is not active without a Spring context,
 * so {@code fetchVessels} runs the real method body directly.</p>
 */
@ExtendWith(MockitoExtension.class)
class AisAdapterTest {

    private static final double LON_MIN = -49.0;
    private static final double LAT_MIN = -28.5;
    private static final double LON_MAX = -35.0;
    private static final double LAT_MAX = -22.5;
    private static final int ZOOM = 6;

    @Mock
    private AisFeignClient feignClient;

    private AisAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AisAdapter(feignClient);
    }

    // -------------------------------------------------------------------------
    // fetchVessels — delegation
    // -------------------------------------------------------------------------

    @Test
    void fetchVessels_delegatesToFeignClient() {
        List<Vessel.VesselData> expected = List.of(vesselData(1L));
        when(feignClient.getVessels(LON_MIN, LAT_MIN, LON_MAX, LAT_MAX, ZOOM)).thenReturn(expected);

        List<Vessel.VesselData> result = adapter.fetchVessels(LON_MIN, LAT_MIN, LON_MAX, LAT_MAX, ZOOM);

        assertThat(result).isSameAs(expected);
        verify(feignClient).getVessels(LON_MIN, LAT_MIN, LON_MAX, LAT_MAX, ZOOM);
    }

    @Test
    void fetchVessels_returnsEmptyListWhenFeignReturnsEmpty() {
        when(feignClient.getVessels(LON_MIN, LAT_MIN, LON_MAX, LAT_MAX, ZOOM)).thenReturn(List.of());

        List<Vessel.VesselData> result = adapter.fetchVessels(LON_MIN, LAT_MIN, LON_MAX, LAT_MAX, ZOOM);

        assertThat(result).isEmpty();
    }

    @Test
    void fetchVessels_returnsMultipleVessels() {
        List<Vessel.VesselData> vessels = List.of(vesselData(1L), vesselData(2L), vesselData(3L));
        when(feignClient.getVessels(LON_MIN, LAT_MIN, LON_MAX, LAT_MAX, ZOOM)).thenReturn(vessels);

        List<Vessel.VesselData> result = adapter.fetchVessels(LON_MIN, LAT_MIN, LON_MAX, LAT_MAX, ZOOM);

        assertThat(result).hasSize(3);
    }

    @Test
    void fetchVessels_forwardsCustomBoundingBox() {
        double customLonMin = -46.0;
        double customLatMin = -24.0;
        double customLonMax = -43.0;
        double customLatMax = -22.0;
        int customZoom = 10;

        when(feignClient.getVessels(customLonMin, customLatMin, customLonMax, customLatMax, customZoom))
                .thenReturn(List.of());

        adapter.fetchVessels(customLonMin, customLatMin, customLonMax, customLatMax, customZoom);

        verify(feignClient).getVessels(customLonMin, customLatMin, customLonMax, customLatMax, customZoom);
    }

    // -------------------------------------------------------------------------
    // Fallback
    // -------------------------------------------------------------------------

    @Test
    void fallback_returnsEmptyList() {
        List<Vessel.VesselData> result =
                adapter.fetchVesselsFallback(LON_MIN, LAT_MIN, LON_MAX, LAT_MAX, ZOOM,
                        new RuntimeException("circuit open"));

        assertThat(result).isEmpty();
    }

    @Test
    void fallback_returnsEmptyList_onAnyException() {
        List<Vessel.VesselData> result =
                adapter.fetchVesselsFallback(LON_MIN, LAT_MIN, LON_MAX, LAT_MAX, ZOOM,
                        new IllegalStateException("timeout"));

        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Vessel.VesselData vesselData(long id) {
        return new Vessel.VesselData(
                id, id, "cargo", 0L, 123456789L,
                "VESSEL_" + id, "VESSEL_AIS_" + id,
                70L, 71L, System.currentTimeMillis(),
                100L, 20L, 50L, 50L, 10L, 10L,
                0L, 90.0, 5.5, 6.0,
                0L, "BR",
                -23.5, -46.5, 0L, 0L
        );
    }
}
