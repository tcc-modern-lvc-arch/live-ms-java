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
    void fetchVessels_delegatesToFeignClientAndMapsToDomain() {
        when(feignClient.getVessels(LON_MIN, LAT_MIN, LON_MAX, LAT_MAX, ZOOM)).thenReturn(List.of(response(1L)));

        List<Vessel.VesselData> result = adapter.fetchVessels(LON_MIN, LAT_MIN, LON_MAX, LAT_MAX, ZOOM);

        assertThat(result).containsExactly(new Vessel.VesselData(
                1L, 123456790L, "VESSEL_1", "Type_71", -23.5, -46.5, 1_700_000_000L, 5.5, 90));
        verify(feignClient).getVessels(LON_MIN, LAT_MIN, LON_MAX, LAT_MAX, ZOOM);
    }

    @Test
    void fetchVessels_returnsEmptyListWhenFeignReturnsNull() {
        when(feignClient.getVessels(LON_MIN, LAT_MIN, LON_MAX, LAT_MAX, ZOOM)).thenReturn(null);

        assertThat(adapter.fetchVessels(LON_MIN, LAT_MIN, LON_MAX, LAT_MAX, ZOOM)).isEmpty();
    }

    @Test
    void fetchVessels_returnsEmptyListWhenFeignReturnsEmpty() {
        when(feignClient.getVessels(LON_MIN, LAT_MIN, LON_MAX, LAT_MAX, ZOOM)).thenReturn(List.of());

        List<Vessel.VesselData> result = adapter.fetchVessels(LON_MIN, LAT_MIN, LON_MAX, LAT_MAX, ZOOM);

        assertThat(result).isEmpty();
    }

    @Test
    void fetchVessels_returnsMultipleVessels() {
        when(feignClient.getVessels(LON_MIN, LAT_MIN, LON_MAX, LAT_MAX, ZOOM))
                .thenReturn(List.of(response(1L), response(2L), response(3L)));

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

    private static AisVesselResponse response(long id) {
        return new AisVesselResponse(id, 123456789L + id, "VESSEL_" + id, "VESSEL_AIS_" + id,
                71L, 1_700_000_000L, 5.5, 90L, -23.5, -46.5);
    }
}
