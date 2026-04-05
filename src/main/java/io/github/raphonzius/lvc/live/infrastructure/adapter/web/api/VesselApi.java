package io.github.raphonzius.lvc.live.infrastructure.adapter.web.api;

import io.github.raphonzius.lvc.live.domain.vessel.Vessel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * API contract for AIS vessel endpoints — v1.
 * Full documentation lives in src/main/resources/static/openapi.yaml.
 */
@RequestMapping("/api/vessels")
public interface VesselApi {

    @GetMapping(value = "/bounding-box", version = "1.0")
    ResponseEntity<List<Vessel.VesselData>> getVessels(
            @RequestParam(defaultValue = "-49.0000") double lonMin,
            @RequestParam(defaultValue = "-28.5000") double latMin,
            @RequestParam(defaultValue = "-35.0000") double lonMax,
            @RequestParam(defaultValue = "-22.5000") double latMax,
            @RequestParam(defaultValue = "6") int zoom
    );

    @PostMapping(value = "/poll", version = "1.0")
    ResponseEntity<String> manualPoll();

    @GetMapping(value = "/health", version = "1.0")
    ResponseEntity<String> health();
}
