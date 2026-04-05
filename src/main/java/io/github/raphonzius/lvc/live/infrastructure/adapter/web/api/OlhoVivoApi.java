package io.github.raphonzius.lvc.live.infrastructure.adapter.web.api;

import io.github.raphonzius.lvc.live.domain.bus.BusLine;
import io.github.raphonzius.lvc.live.domain.bus.VehiclePosition;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * API contract for SPTrans OlhoVivo bus endpoints — v1.
 * Full documentation lives in src/main/resources/static/openapi.yaml.
 */
@RequestMapping("/api/buses")
public interface OlhoVivoApi {

    @GetMapping(value = "/lines", version = "1.0")
    ResponseEntity<List<BusLine.LineData>> searchLines(
            @RequestParam("q") String terms
    );

    @GetMapping(value = "/positions/line", version = "1.0")
    ResponseEntity<VehiclePosition.PositionResponse> positionsByLine(
            @RequestParam("codigoLinha") int lineCode
    );

    @PostMapping(value = "/poll", version = "1.0")
    ResponseEntity<String> manualPoll();

    @GetMapping(value = "/health", version = "1.0")
    ResponseEntity<String> health();
}
