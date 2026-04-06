package io.github.raphonzius.lvc.live.infrastructure.adapter.web.api;

import io.github.raphonzius.lvc.live.domain.flooding.FloodingPoint;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * API contract for CGESP flooding endpoints — v1.
 */
@RequestMapping("/api/floodings")
public interface CgespApi {

    /**
     * Returns flooding points for the given date (or today if omitted).
     *
     * @param date optional date in DD/MM/YYYY format (defaults to today in São Paulo timezone)
     * @return list of flooding points scraped from CGESP
     */
    @GetMapping(version = "1.0")
    ResponseEntity<List<FloodingPoint.FloodData>> getFloodings(
            @RequestParam(value = "date", required = false) String date
    );

    /**
     * Returns flooding points for every day in the given inclusive date range.
     * Maximum range is controlled by {@code cgesp.max-range-days} in application.yaml.
     *
     * @param from start date in DD/MM/YYYY format (required)
     * @param to   end date in DD/MM/YYYY format (required)
     * @return flat list of flooding points across all queried dates
     */
    @GetMapping(value = "/range", version = "1.0")
    ResponseEntity<List<FloodingPoint.FloodData>> getFloodingsByRange(
            @RequestParam("from") String from,
            @RequestParam("to") String to
    );

    /**
     * Manually triggers a CGESP polling cycle and publishes results to {@code floodings:stream}.
     *
     * @return 202 Accepted with confirmation message
     */
    @PostMapping(value = "/poll", version = "1.0")
    ResponseEntity<String> manualPoll();

    /**
     * Liveness check for the CGESP flooding service.
     *
     * @return 200 OK with status message
     */
    @GetMapping(value = "/health", version = "1.0")
    ResponseEntity<String> health();
}
