package io.github.raphonzius.lvc.live.infrastructure.input.external.ais;

import io.github.raphonzius.lvc.live.domain.FetchResult;
import io.github.raphonzius.lvc.live.domain.vessel.Vessel;
import io.github.raphonzius.lvc.live.infrastructure.input.external.UpstreamTelemetry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AisAdapter} and {@link AisUpstream}.
 *
 * <p>{@code @CircuitBreaker} AOP is not active without a Spring context,
 * so the real method bodies run directly.</p>
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

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private AisUpstream upstream;
    private AisAdapter adapter;

    @BeforeEach
    void setUp() {
        var telemetry = new UpstreamTelemetry(registry, Clock.systemUTC(), Duration.ofMinutes(5));
        upstream = new AisUpstream(feignClient, telemetry);
        adapter = new AisAdapter(upstream, telemetry);
    }

    private List<Vessel.VesselData> fetch() {
        return switch (adapter.fetchVessels(LON_MIN, LAT_MIN, LON_MAX, LAT_MAX, ZOOM)) {
            case FetchResult.Fetched<List<Vessel.VesselData>>(var data) -> data;
            case FetchResult.Unavailable<List<Vessel.VesselData>>(var reason) ->
                    throw new AssertionError("unexpected Unavailable: " + reason);
        };
    }

    // -------------------------------------------------------------------------
    // fetchVessels — delegation and mapping
    // -------------------------------------------------------------------------

    @Test
    void fetchVessels_delegatesToFeignClientAndMapsToDomain() {
        when(feignClient.getVessels(LON_MIN, LAT_MIN, LON_MAX, LAT_MAX, ZOOM)).thenReturn(List.of(response(1L)));

        var result = fetch();

        assertThat(result).singleElement().satisfies(v -> {
            assertThat(v.id()).isEqualTo(1L);
            assertThat(v.mmsi()).isEqualTo(123456790L);
            assertThat(v.name()).isEqualTo("VESSEL_1");
            assertThat(v.shipType()).isEqualTo("Type_71");
            assertThat(v.latitude()).isEqualTo(-23.5);
            assertThat(v.longitude()).isEqualTo(-46.5);
            assertThat(v.timestamp()).isEqualTo(1_700_000_000L);
            assertThat(v.speedKnots()).isEqualTo(5.5);
            assertThat(v.headingDeg()).isEqualTo(90);
        });
        verify(feignClient).getVessels(LON_MIN, LAT_MIN, LON_MAX, LAT_MAX, ZOOM);
    }

    @Test
    void fetchVessels_returnsEmptyListWhenFeignReturnsNull() {
        when(feignClient.getVessels(LON_MIN, LAT_MIN, LON_MAX, LAT_MAX, ZOOM)).thenReturn(null);

        assertThat(fetch()).isEmpty();
    }

    @Test
    void fetchVessels_returnsEmptyListWhenFeignReturnsEmpty() {
        when(feignClient.getVessels(LON_MIN, LAT_MIN, LON_MAX, LAT_MAX, ZOOM)).thenReturn(List.of());

        assertThat(fetch()).isEmpty();
    }

    @Test
    void fetchVessels_returnsMultipleVessels() {
        when(feignClient.getVessels(LON_MIN, LAT_MIN, LON_MAX, LAT_MAX, ZOOM))
                .thenReturn(List.of(response(1L), response(2L), response(3L)));

        assertThat(fetch()).hasSize(3);
    }

    @Test
    void fetchVessels_forwardsCustomBoundingBox() {
        when(feignClient.getVessels(-46.0, -24.0, -43.0, -22.0, 10)).thenReturn(List.of());

        adapter.fetchVessels(-46.0, -24.0, -43.0, -22.0, 10);

        verify(feignClient).getVessels(-46.0, -24.0, -43.0, -22.0, 10);
    }

    // -------------------------------------------------------------------------
    // Per-item isolation
    // -------------------------------------------------------------------------

    @Test
    void invalidRecordsAreDroppedAndCountedWithoutLosingTheValidOnes() {
        var noId = new AisVesselResponse(null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, -1.0, -2.0, null, null);
        var noLat = new AisVesselResponse(9L, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, -2.0, null, null);
        when(feignClient.getVessels(LON_MIN, LAT_MIN, LON_MAX, LAT_MAX, ZOOM))
                .thenReturn(Arrays.asList(response(1L), noId, null, noLat, response(2L)));

        var result = fetch();

        assertThat(result).extracting(Vessel.VesselData::id).containsExactly(1L, 2L);
        assertThat(registry.get("lvc.upstream.invalid_records").tag("api", "ais").counter().count())
                .isEqualTo(3.0);
    }

    @Test
    void absurdHeadingDoesNotMakeTheWholeSnapshotUnavailable() {
        var absurd = new AisVesselResponse(3L, null, null, null, 3L, "X", null, null, null, null, null, null,
                null, null, null, null, 3_000_000_000L, null, null, null, null, null, -1.0, -2.0, null, null);
        when(feignClient.getVessels(LON_MIN, LAT_MIN, LON_MAX, LAT_MAX, ZOOM))
                .thenReturn(List.of(response(1L), absurd));

        var result = fetch();

        assertThat(result).hasSize(2);
        assertThat(result.get(1).headingDeg()).isNull();
    }

    // -------------------------------------------------------------------------
    // Fallback
    // -------------------------------------------------------------------------

    @Test
    void fallback_isUnavailableNotEmpty_andCounted() {
        var result = upstream.fetchFallback(LON_MIN, LAT_MIN, LON_MAX, LAT_MAX, ZOOM,
                new IllegalStateException("timeout"));

        assertThat(result).isEqualTo(FetchResult.unavailable("IllegalStateException"));
        assertThat(registry.get("lvc.upstream.fallback").tags("api", "ais", "reason", "IllegalStateException")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void fallback_circuitOpenReason() {
        var open = CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("ais-api"));

        var result = upstream.fetchFallback(LON_MIN, LAT_MIN, LON_MAX, LAT_MAX, ZOOM, open);

        assertThat(result).isEqualTo(FetchResult.unavailable("circuit_open"));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static AisVesselResponse response(long id) {
        return new AisVesselResponse(id, null, null, null, 123456789L + id, "VESSEL_" + id, "VESSEL_AIS_" + id,
                null, 71L, 1_700_000_000L, null, null, null, null, null, null, 90L, null, 5.5, null, null, null,
                -23.5, -46.5, null, null);
    }
}
