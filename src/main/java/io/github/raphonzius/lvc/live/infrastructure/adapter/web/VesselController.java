package io.github.raphonzius.lvc.live.infrastructure.adapter.web;

import io.github.raphonzius.lvc.live.application.service.AisService;
import io.github.raphonzius.lvc.live.domain.vessel.AisPort;
import io.github.raphonzius.lvc.live.infrastructure.adapter.web.api.VesselApi;
import io.github.raphonzius.lvc.live.infrastructure.adapter.web.dto.VesselV1Response;
import io.github.raphonzius.lvc.live.infrastructure.scheduler.AisPollingScheduler;
import io.github.raphonzius.lvc.live.infrastructure.scheduler.PollingJobRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for AIS vessel data — v1.
 * All endpoint mappings and OpenAPI annotations live in {@link VesselApi}.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class VesselController implements VesselApi {

    private final AisPort aisPort;
    private final AisService aisService;
    private final PollingJobRunner pollingJobRunner;
    private final AisPollingScheduler aisPollingJob;

    /** {@inheritDoc} */
    @Override
    public ResponseEntity<List<VesselV1Response>> getVessels(
            double lonMin, double latMin, double lonMax, double latMax, int zoom
    ) {
        log.info("REST: getVessels box=({},{})→({},{})", lonMin, latMin, lonMax, latMax);
        // v1.0 keeps answering 200 with an empty list while AIS is unavailable (unchanged behavior);
        // the outage is visible in lvc.upstream.fallback and in the logs.
        var vessels = aisPort.fetchVessels(lonMin, latMin, lonMax, latMax, zoom).orElse(List.of());
        return ResponseEntity.ok(vessels.stream().map(VesselV1Response::from).toList());
    }

    /** {@inheritDoc} */
    @Override
    public ResponseEntity<String> manualPoll() {
        log.info("REST: manual AIS poll triggered");
        // Same path as the scheduler: correlation id, logs and lvc.polling.duration metric.
        pollingJobRunner.runOrThrow(aisPollingJob);
        return ResponseEntity.accepted().body("Polling initiated");
    }

    /** {@inheritDoc} */
    @Override
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Vessel service is running");
    }
}
