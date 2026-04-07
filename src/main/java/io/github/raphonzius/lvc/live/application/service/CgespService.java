package io.github.raphonzius.lvc.live.application.service;

import io.github.raphonzius.lvc.live.application.exception.InvalidRangeException;
import io.github.raphonzius.lvc.live.domain.TimeZones;
import io.github.raphonzius.lvc.live.domain.flooding.FloodingPoint;
import io.github.raphonzius.lvc.live.domain.flooding.FloodingPort;
import io.github.raphonzius.lvc.live.domain.streaming.FloodingStreamingPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Application service for CGESP flooding data orchestration.
 *
 * <p>Polling workflow:
 * <ol>
 *   <li>Fetch today's flooding points via {@link FloodingPort}</li>
 *   <li>Publish results to {@code floodings:stream} via {@link FloodingStreamingPort}</li>
 * </ol>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CgespService {

    private static final ZoneId SAO_PAULO_TZ = TimeZones.SAO_PAULO;

    private final FloodingPort floodingPort;
    private final FloodingStreamingPort floodingStreamingPort;

    @Value("${cgesp.max-range-days:30}")
    private int maxRangeDays;

    /**
     * Fetches today's flooding points (São Paulo timezone) and publishes them to Redis.
     */
    public void fetchAndStreamFloodings() {
        LocalDate today = LocalDate.now(SAO_PAULO_TZ);
        List<FloodingPoint.FloodData> floodings = floodingPort.fetchFloodings(today);
        log.info("Fetched {} flooding point(s) for date={}", floodings.size(), today);
        floodingStreamingPort.publishFloodings(today, floodings);
    }

    /**
     * Fetches flooding points for a specific date without streaming.
     *
     * @param date the date to query
     * @return list of flooding points
     */
    public List<FloodingPoint.FloodData> fetchFloodingsByDate(LocalDate date) {
        return floodingPort.fetchFloodings(date);
    }

    /**
     * Fetches flooding points for every date in [from, to], inclusive.
     * Dates are queried sequentially to avoid hammering the CGESP website.
     * Throws {@link InvalidRangeException} if the range exceeds {@code cgesp.max-range-days}.
     *
     * @param from start date (inclusive)
     * @param to   end date (inclusive)
     * @return flat list of all flooding points across the range
     */
    public List<FloodingPoint.FloodData> fetchFloodingsByRange(LocalDate from, LocalDate to) {
        long days = ChronoUnit.DAYS.between(from, to);
        int maxDays = this.maxRangeDays;
        if (days >= maxDays) {
            throw new InvalidRangeException(
                    "Range spans " + (days + 1) + " days — maximum is " + maxDays);
        }
        return from.datesUntil(to.plusDays(1))
                .flatMap(date -> floodingPort.fetchFloodings(date).stream())
                .toList();
    }
}
