package io.github.raphonzius.lvc.live.infrastructure.output.grpc;

import io.github.raphonzius.lvc.live.domain.streaming.VesselStreamingPort;
import io.github.raphonzius.lvc.live.domain.vessel.Vessel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Stream;

/**
 * Publishes AIS vessels to the EventHub. Mapping lives in {@link EventRequestMapper};
 * concurrency, deadlines and failure accounting in {@link EventPublisher}.
 */
@Component
@RequiredArgsConstructor
public class GrpcVesselStreamingAdapter implements VesselStreamingPort {

    private final EventRequestMapper mapper;
    private final EventPublisher publisher;

    @Override
    public void publishVessel(Vessel vessel) {
        publisher.publish("vessel", List.of(mapper.toEvent(vessel)));
    }

    @Override
    public void publishVesselBatch(Stream<? extends Vessel> vesselStream) {
        publisher.publish("vessel", vesselStream.map(mapper::toEvent).toList());
    }
}
