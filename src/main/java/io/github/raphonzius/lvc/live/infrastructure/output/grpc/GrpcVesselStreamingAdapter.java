package io.github.raphonzius.lvc.live.infrastructure.output.grpc;

import io.github.raphonzius.lvc.live.domain.streaming.PublishResult;
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
    public PublishResult publishVessel(Vessel vessel) {
        return publishVesselBatch(Stream.of(vessel));
    }

    @Override
    public PublishResult publishVesselBatch(Stream<? extends Vessel> vesselStream) {
        List<Vessel> vessels = vesselStream.<Vessel>map(v -> v).toList();
        return EventBatches.publish(publisher, "vessel",
                EventBatches.mapEach("vessel", vessels, (Vessel v) -> mapper.toEvent(v)));
    }
}
