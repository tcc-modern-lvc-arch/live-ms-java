package io.github.raphonzius.lvc.live.infrastructure.output.grpc;

import io.github.raphonzius.lvc.live.domain.flooding.FloodingPoint;
import io.github.raphonzius.lvc.live.domain.streaming.FloodingStreamingPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Publishes CGESP flooding points to the EventHub. Mapping lives in {@link EventRequestMapper};
 * concurrency, deadlines and failure accounting in {@link EventPublisher}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GrpcFloodingStreamingAdapter implements FloodingStreamingPort {

    private final EventRequestMapper mapper;
    private final EventPublisher publisher;

    @Override
    public void publishFloodings(LocalDate date, List<FloodingPoint.FloodData> floodings) {
        if (floodings == null || floodings.isEmpty()) {
            log.debug("No flooding points to publish for date={}", date);
            return;
        }
        publisher.publish("flooding", floodings.stream().map(mapper::toEvent).toList());
    }
}
