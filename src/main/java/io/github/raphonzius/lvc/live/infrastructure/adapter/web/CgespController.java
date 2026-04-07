package io.github.raphonzius.lvc.live.infrastructure.adapter.web;

import io.github.raphonzius.lvc.live.application.service.CgespService;
import io.github.raphonzius.lvc.live.domain.flooding.FloodingPoint;
import io.github.raphonzius.lvc.live.infrastructure.adapter.web.api.CgespApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import io.github.raphonzius.lvc.live.domain.TimeZones;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * REST controller for CGESP flooding data — v1.
 * All endpoint mappings and OpenAPI annotations live in {@link CgespApi}.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class CgespController implements CgespApi {

    private static final ZoneId SAO_PAULO_TZ = TimeZones.SAO_PAULO;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CgespService cgespService;

    /** {@inheritDoc} */
    @Override
    public ResponseEntity<List<FloodingPoint.FloodData>> getFloodings(String date) {
        LocalDate queryDate;
        if (date == null || date.isBlank()) {
            queryDate = LocalDate.now(SAO_PAULO_TZ);
        } else {
            try {
                queryDate = LocalDate.parse(date, DATE_FMT);
            } catch (DateTimeParseException e) {
                log.warn("Invalid date format '{}' — expected DD/MM/YYYY", date);
                return ResponseEntity.badRequest().build();
            }
        }
        log.info("REST: getFloodings date={}", queryDate);
        return ResponseEntity.ok(cgespService.fetchFloodingsByDate(queryDate));
    }

    /** {@inheritDoc} */
    @Override
    public ResponseEntity<List<FloodingPoint.FloodData>> getFloodingsByRange(String from, String to) {
        LocalDate fromDate;
        LocalDate toDate;
        try {
            fromDate = LocalDate.parse(from, DATE_FMT);
            toDate   = LocalDate.parse(to,   DATE_FMT);
        } catch (DateTimeParseException e) {
            log.warn("Invalid date format in range query from='{}' to='{}' — expected DD/MM/YYYY", from, to);
            return ResponseEntity.badRequest().build();
        }

        if (fromDate.isAfter(toDate)) {
            log.warn("Range query: 'from' ({}) is after 'to' ({})", fromDate, toDate);
            return ResponseEntity.badRequest().build();
        }

        log.info("REST: getFloodingsByRange from={} to={}", fromDate, toDate);
        return ResponseEntity.ok(cgespService.fetchFloodingsByRange(fromDate, toDate));
    }

    /** {@inheritDoc} */
    @Override
    public ResponseEntity<String> manualPoll() {
        log.info("REST: manual CGESP poll triggered");
        cgespService.fetchAndStreamFloodings();
        return ResponseEntity.accepted().body("CGESP flooding polling initiated");
    }

    /** {@inheritDoc} */
    @Override
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("CGESP flooding service is running");
    }
}
