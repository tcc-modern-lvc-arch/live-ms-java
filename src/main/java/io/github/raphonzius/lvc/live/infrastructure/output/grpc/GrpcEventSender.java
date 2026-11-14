package io.github.raphonzius.lvc.live.infrastructure.output.grpc;

import io.github.raphonzius.lvc.proto.event.EventAck;
import io.github.raphonzius.lvc.proto.event.EventHubGrpc;
import io.github.raphonzius.lvc.proto.event.EventRequest;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * {@link EventSender} over the blocking {@code EventHub.SendEvent} RPC.
 *
 * <p>Every call gets its own deadline ({@code event-hub.grpc.deadline}); the deadline must be
 * set per call, since a deadline on a shared stub would be an absolute instant. Retries on
 * {@code UNAVAILABLE} are configured on the channel (service config), so they happen inside
 * this deadline.</p>
 */
public final class GrpcEventSender implements EventSender {

    private final EventHubGrpc.EventHubBlockingStub stub;
    private final long deadlineMillis;

    public GrpcEventSender(EventHubGrpc.EventHubBlockingStub stub, Duration deadline) {
        this.stub = Objects.requireNonNull(stub, "stub");
        this.deadlineMillis = deadline.toMillis();
    }

    @Override
    public EventAck send(EventRequest request) {
        return stub.withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS).sendEvent(request);
    }
}
