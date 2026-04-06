package io.github.raphonzius.lvc.live.domain.streaming;

import io.github.raphonzius.lvc.live.domain.flooding.FloodingPoint;

import java.time.LocalDate;
import java.util.List;

/**
 * Domain port for publishing flooding data to a stream.
 */
public interface FloodingStreamingPort {

    /**
     * Publishes flooding points to the stream.
     *
     * @param date      the date the flooding data was collected
     * @param floodings list of flooding points to publish
     */
    void publishFloodings(LocalDate date, List<FloodingPoint.FloodData> floodings);
}
