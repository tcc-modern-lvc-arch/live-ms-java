package io.github.raphonzius.lvc.live.infrastructure.output.grpc;

import io.github.raphonzius.lvc.live.domain.streaming.PublishResult;
import io.github.raphonzius.lvc.proto.event.EventRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * Item-by-item domain → event mapping shared by the streaming adapters: one item the mapper
 * cannot handle is counted as a failed event and logged once per batch (with the first cause),
 * instead of aborting the whole batch.
 */
@Slf4j
final class EventBatches {

    private EventBatches() {
    }

    /**
     * @param events      successfully mapped events, in input order
     * @param mapFailures number of items that could not be mapped
     */
    record Mapped(List<EventRequest> events, int mapFailures) {
    }

    static <T> Mapped mapEach(String stream, Collection<T> items, Function<T, EventRequest> mapper) {
        var events = new ArrayList<EventRequest>(items.size());
        var failures = 0;
        RuntimeException first = null;
        for (var item : items) {
            try {
                events.add(mapper.apply(item));
            } catch (RuntimeException e) {
                failures++;
                if (first == null) {
                    first = e;
                }
            }
        }
        if (failures > 0) {
            log.error("[{}] {} of {} item(s) could not be mapped to events and were counted as failed (first cause below)",
                    stream, failures, items.size(), first);
        }
        return new Mapped(List.copyOf(events), failures);
    }

    /** Publishes the mapped events and folds the mapping failures into the result. */
    static PublishResult publish(EventPublisher publisher, String stream, Mapped mapped) {
        var delivered = publisher.publish(stream, mapped.events()).toResult();
        return delivered.plus(PublishResult.failedOf(mapped.mapFailures()));
    }
}
