package io.github.raphonzius.lvc.live.application.service;

import io.github.raphonzius.lvc.live.domain.streaming.VesselStreamingPort;
import io.github.raphonzius.lvc.live.domain.vessel.AisPort;
import io.github.raphonzius.lvc.live.domain.vessel.Vessel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AisService} orchestration.
 */
@ExtendWith(MockitoExtension.class)
class AisServiceTest {

    // Brazil coast bounding box used by fetchAndStreamVessels()
    private static final double LON_MIN = -49.0;
    private static final double LAT_MIN = -28.5;
    private static final double LON_MAX = -35.0;
    private static final double LAT_MAX = -22.5;
    private static final int ZOOM = 6;

    @Mock
    private AisPort aisPort;

    @Mock
    private VesselStreamingPort vesselStreamingPort;

    @InjectMocks
    private AisService service;

    // -------------------------------------------------------------------------
    // fetchAndStreamVessels() — no-arg variant
    // -------------------------------------------------------------------------

    @Test
    void fetchAndStreamVessels_usesBrazilCoastBoundingBox() {
        when(aisPort.fetchVessels(LON_MIN, LAT_MIN, LON_MAX, LAT_MAX, ZOOM))
                .thenReturn(List.of(vesselData(1L)));

        service.fetchAndStreamVessels();

        verify(aisPort).fetchVessels(LON_MIN, LAT_MIN, LON_MAX, LAT_MAX, ZOOM);
    }

    @Test
    void fetchAndStreamVessels_publishesBatchWhenVesselsReturned() {
        List<Vessel.VesselData> vessels = List.of(vesselData(1L), vesselData(2L));
        when(aisPort.fetchVessels(LON_MIN, LAT_MIN, LON_MAX, LAT_MAX, ZOOM)).thenReturn(vessels);

        service.fetchAndStreamVessels();

        ArgumentCaptor<Stream<? extends Vessel>> captor = ArgumentCaptor.captor();
        verify(vesselStreamingPort).publishVesselBatch(captor.capture());
        assertThat(captor.getValue().toList()).hasSize(2);
    }

    @Test
    void fetchAndStreamVessels_skipsPublishWhenPortReturnsEmptyList() {
        when(aisPort.fetchVessels(LON_MIN, LAT_MIN, LON_MAX, LAT_MAX, ZOOM)).thenReturn(List.of());

        service.fetchAndStreamVessels();

        verify(vesselStreamingPort, never()).publishVesselBatch(anyStreamOf());
    }

    @Test
    void fetchAndStreamVessels_skipsPublishWhenPortReturnsNull() {
        when(aisPort.fetchVessels(LON_MIN, LAT_MIN, LON_MAX, LAT_MAX, ZOOM)).thenReturn(null);

        service.fetchAndStreamVessels();

        verify(vesselStreamingPort, never()).publishVesselBatch(anyStreamOf());
    }

    // -------------------------------------------------------------------------
    // fetchAndStreamVessels(params) — parameterised variant
    // -------------------------------------------------------------------------

    @Test
    void fetchAndStreamVessels_withParams_forwardsToPort() {
        double lonMin = -46.0;
        double latMin = -24.0;
        double lonMax = -43.0;
        double latMax = -22.0;
        int zoom = 10;

        when(aisPort.fetchVessels(lonMin, latMin, lonMax, latMax, zoom))
                .thenReturn(List.of(vesselData(42L)));

        service.fetchAndStreamVessels(lonMin, latMin, lonMax, latMax, zoom);

        verify(aisPort).fetchVessels(lonMin, latMin, lonMax, latMax, zoom);
    }

    @Test
    void fetchAndStreamVessels_withParams_publishesSingleVessel() {
        Vessel.VesselData v = vesselData(7L);
        when(aisPort.fetchVessels(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(v));

        service.fetchAndStreamVessels(LON_MIN, LAT_MIN, LON_MAX, LAT_MAX, ZOOM);

        ArgumentCaptor<Stream<? extends Vessel>> captor = ArgumentCaptor.captor();
        verify(vesselStreamingPort).publishVesselBatch(captor.capture());
        assertThat(captor.getValue().toList()).hasSize(1);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static Stream<? extends Vessel> anyStreamOf() {
        return (Stream<? extends Vessel>) (Stream<?>) org.mockito.ArgumentMatchers.any(Stream.class);
    }

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
