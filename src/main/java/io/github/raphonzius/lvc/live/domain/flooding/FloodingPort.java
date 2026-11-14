package io.github.raphonzius.lvc.live.domain.flooding;

import io.github.raphonzius.lvc.live.domain.FetchResult;

import java.time.LocalDate;
import java.util.List;

/**
 * Domain port for fetching CGESP flooding data.
 */
public interface FloodingPort {

    /**
     * Fetches flooding points for the given date.
     *
     * @param date the date to query (São Paulo timezone)
     * @return the flooding points (possibly empty), or {@code Unavailable} when the source failed
     */
    FetchResult<List<FloodingPoint.FloodData>> fetchFloodings(LocalDate date);
}
