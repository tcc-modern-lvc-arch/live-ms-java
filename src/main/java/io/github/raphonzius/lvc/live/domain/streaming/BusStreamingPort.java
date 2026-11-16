package io.github.raphonzius.lvc.live.domain.streaming;

import io.github.raphonzius.lvc.live.domain.bus.VehiclePosition;

/**
 * Port interface for streaming bus position data to other microservices.
 * Publishes to Event Hub via gRPC.
 */
public interface BusStreamingPort {

    /**
     * Publishes a vehicle position snapshot to the streaming channel.
     *
     * @param positions the position response to publish
     * @return how many events were delivered / failed
     */
    PublishResult publishPositions(VehiclePosition.PositionResponse positions);
}
