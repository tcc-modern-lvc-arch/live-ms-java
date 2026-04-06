package io.github.raphonzius.lvc.live.infrastructure.adapter.web;

import io.github.raphonzius.lvc.live.application.service.OlhoVivoService;
import io.github.raphonzius.lvc.live.domain.bus.BusLine;
import io.github.raphonzius.lvc.live.domain.bus.VehiclePosition;
import io.github.raphonzius.lvc.live.infrastructure.adapter.web.api.OlhoVivoApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for SPTrans OlhoVivo bus data — v1.
 * All endpoint mappings and OpenAPI annotations live in {@link OlhoVivoApi}.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class OlhoVivoController implements OlhoVivoApi {

    private final OlhoVivoService olhoVivoService;

    /** {@inheritDoc} */
    @Override
    public ResponseEntity<List<BusLine.LineData>> searchLines(String terms) {
        log.info("REST: searchLines q={}", terms);
        return ResponseEntity.ok(olhoVivoService.searchLines(terms));
    }

    /** {@inheritDoc} */
    @Override
    public ResponseEntity<VehiclePosition.PositionResponse> positionsByLine(int lineCode) {
        log.info("REST: positionsByLine codigoLinha={}", lineCode);
        return ResponseEntity.ok(olhoVivoService.positionsByLine(lineCode));
    }

    /** {@inheritDoc} */
    @Override
    public ResponseEntity<String> manualPoll() {
        log.info("REST: manual OlhoVivo poll triggered");
        olhoVivoService.fetchAndStreamVehiclePositions();
        return ResponseEntity.accepted().body("OlhoVivo polling initiated");
    }

    /** {@inheritDoc} */
    @Override
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OlhoVivo bus service is running");
    }
}
