package io.github.raphonzius.lvc.live.quarkus.domain;

import java.util.stream.Stream;

public interface VesselStreamingPort {
    void publishVessel(Vessel vessel);

    void publishVesselBatch(Stream<? extends Vessel> vesselStream);
}
