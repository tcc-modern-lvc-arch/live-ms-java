package io.github.raphonzius.lvc.live.infrastructure.input.external.ais;

import io.github.raphonzius.lvc.live.domain.FetchResult;
import io.github.raphonzius.lvc.live.domain.vessel.AisPort;
import io.github.raphonzius.lvc.live.domain.vessel.Vessel;
import io.github.raphonzius.lvc.live.infrastructure.input.external.UpstreamTelemetry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * AIS adapter implementing the AisPort interface.
 *
 * Location: infrastructure/input/external/ais/
 * This adapter IMPLEMENTS the domain port AisPort.
 *
 * <p>The HTTP call (with its circuit breaker and fallback) lives in {@link AisUpstream}.
 * This class maps the AIS Friends wire format ({@link AisVesselResponse}) into domain records
 * <em>item by item</em>, outside the breaker: a record without id/latitude/longitude is dropped
 * (warning rate-limited, counter {@code lvc.upstream.invalid_records{api=ais}}) and never takes
 * the rest of the snapshot down.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AisAdapter implements AisPort {

    private final AisUpstream upstream;
    private final UpstreamTelemetry telemetry;

    @Override
    public FetchResult<List<Vessel.VesselData>> fetchVessels(
            double lonMin,
            double latMin,
            double lonMax,
            double latMax,
            int zoom
    ) {
        log.info("Fetching vessels from AIS API - Box: ({},{})-({},{}), Zoom: {}",
                lonMin, latMin, lonMax, latMax, zoom);

        return switch (upstream.fetch(lonMin, latMin, lonMax, latMax, zoom)) {
            case FetchResult.Unavailable<List<AisVesselResponse>>(var reason) -> FetchResult.unavailable(reason);
            case FetchResult.Fetched<List<AisVesselResponse>>(var raw) -> {
                var vessels = mapAll(raw);
                log.info("Successfully fetched {} vessels from AIS API ({} received)", vessels.size(), raw.size());
                yield FetchResult.of(vessels);
            }
        };
    }

    private List<Vessel.VesselData> mapAll(List<AisVesselResponse> raw) {
        var vessels = new ArrayList<Vessel.VesselData>(raw.size());
        for (var item : raw) {
            var invalid = item == null ? "null element" : item.invalidReason();
            if (invalid != null) {
                telemetry.invalidRecord(AisUpstream.API, invalid);
                continue;
            }
            try {
                vessels.add(item.toDomain());
            } catch (RuntimeException e) {
                telemetry.invalidRecord(AisUpstream.API, "mapping failed: " + e);
            }
        }
        return List.copyOf(vessels);
    }
}
