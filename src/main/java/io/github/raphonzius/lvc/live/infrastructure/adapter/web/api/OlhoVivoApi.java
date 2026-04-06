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

    /**
     * Searches bus lines by term via the OlhoVivo API.
     *
     * @param terms search term (e.g. "178L")
     * @return list of matching {@link BusLine.LineData} records
     */
    @GetMapping(value = "/lines", version = "1.0")
    ResponseEntity<List<BusLine.LineData>> searchLines(
            @RequestParam("q") String terms
    );

    /**
     * Returns current vehicle positions for a specific bus line.
     *
     * @param lineCode line code ({@code cl}) from OlhoVivo
     * @return position snapshot including timestamp and vehicle list
     */
    @GetMapping(value = "/positions/line", version = "1.0")
    ResponseEntity<VehiclePosition.PositionResponse> positionsByLine(
            @RequestParam("codigoLinha") int lineCode
    );

    /**
     * Manually triggers an OlhoVivo polling cycle and publishes results to {@code buses:stream}.
     *
     * @return 202 Accepted with confirmation message
     */
    @PostMapping(value = "/poll", version = "1.0")
    ResponseEntity<String> manualPoll();

    /**
     * Liveness check for the OlhoVivo bus service.
     *
     * @return 200 OK with status message
     */
    @GetMapping(value = "/health", version = "1.0")
    ResponseEntity<String> health();
}
