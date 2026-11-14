package io.github.raphonzius.lvc.live.infrastructure.output.grpc;

import io.github.raphonzius.lvc.live.domain.bus.VehiclePosition;
import io.github.raphonzius.lvc.live.domain.streaming.BusStreamingPort;
import io.github.raphonzius.lvc.live.domain.streaming.PublishResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Publishes OlhoVivo bus positions to the EventHub. Mapping lives in {@link EventRequestMapper};
 * concurrency, deadlines and failure accounting in {@link EventPublisher}.
 */
@Component
@RequiredArgsConstructor
public class GrpcBusStreamingAdapter implements BusStreamingPort {

    private final EventRequestMapper mapper;
    private final EventPublisher publisher;

    @Override
    public PublishResult publishPositions(VehiclePosition.PositionResponse positions) {
        return publisher.publish("bus", mapper.toEvents(positions)).toResult();
    }
}
