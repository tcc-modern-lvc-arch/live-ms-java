package io.github.raphonzius.lvc.live.domain.streaming;

import io.github.raphonzius.lvc.live.domain.vessel.Vessel;

/**
 * Port interface for streaming vessel data to other microservices.
 * Implements the publisher pattern for data distribution.
 */
public interface VesselStreamingPort {

    /**
     * Publishes vessel data to the streaming channel.
     *
     * @param vessel the vessel data to publish
     */
    void publishVessel(Vessel vessel);

    /**
     * Publishes a batch of vessel data.
     *
     * @param vesselStream stream of vessels to publish
     */
    void publishVesselBatch(java.util.stream.Stream<? extends Vessel> vesselStream);
}

