package io.github.raphonzius.lvc.live.infrastructure.output.grpc;

import io.github.raphonzius.lvc.proto.event.EventAck;
import io.github.raphonzius.lvc.proto.event.EventRequest;

/**
 * Sends one event to the event hub. Implementations may block and may throw
 * {@link io.grpc.StatusRuntimeException}; {@link EventPublisher} handles both.
 */
@FunctionalInterface
public interface EventSender {

    EventAck send(EventRequest request);
}
