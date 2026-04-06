package io.github.raphonzius.lvc.live.infrastructure.adapter.web;

import io.github.raphonzius.lvc.live.application.service.AisService;
import io.github.raphonzius.lvc.live.domain.vessel.AisPort;
import io.github.raphonzius.lvc.live.domain.vessel.Vessel;
import io.github.raphonzius.lvc.live.infrastructure.adapter.web.api.VesselApi;
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

    /** {@inheritDoc} */
    @Override
    public ResponseEntity<List<Vessel.VesselData>> getVessels(
            double lonMin, double latMin, double lonMax, double latMax, int zoom
    ) {
        log.info("REST: getVessels box=({},{})→({},{})", lonMin, latMin, lonMax, latMax);
        return ResponseEntity.ok(aisPort.fetchVessels(lonMin, latMin, lonMax, latMax, zoom));
    }

    /** {@inheritDoc} */
    @Override
    public ResponseEntity<String> manualPoll() {
        log.info("REST: manual AIS poll triggered");
        aisService.fetchAndStreamVessels();
        return ResponseEntity.accepted().body("Polling initiated");
    }

    /** {@inheritDoc} */
    @Override
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Vessel service is running");
    }
}
