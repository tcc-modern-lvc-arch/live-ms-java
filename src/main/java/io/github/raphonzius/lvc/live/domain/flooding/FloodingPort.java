package io.github.raphonzius.lvc.live.domain.flooding;

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
     * @return list of flooding points, empty if none found or on error
     */
    List<FloodingPoint.FloodData> fetchFloodings(LocalDate date);
}
