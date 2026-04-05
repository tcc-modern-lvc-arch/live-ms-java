package io.github.raphonzius.lvc.live.domain.streaming;

import io.github.raphonzius.lvc.live.domain.bus.VehiclePosition;

/**
 * Port interface for streaming bus position data to other microservices.
 * Publishes to the "buses:stream" Redis stream.
 */
public interface BusStreamingPort {

    void publishPositions(VehiclePosition.PositionResponse positions);
}
