package io.github.raphonzius.lvc.live.domain.streaming;

import io.github.raphonzius.lvc.live.domain.bus.VehiclePosition;

/**
 * Port interface for streaming bus position data to other microservices.
 * Publishes to the "buses:stream" Redis stream.
 */
public interface BusStreamingPort {

    /**
     * Publishes a vehicle position snapshot to the {@code buses:stream} Redis stream.
     *
     * @param positions the position response to publish
     */
    void publishPositions(VehiclePosition.PositionResponse positions);
}
