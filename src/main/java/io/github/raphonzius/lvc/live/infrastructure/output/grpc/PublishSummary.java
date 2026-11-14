package io.github.raphonzius.lvc.live.infrastructure.output.grpc;

import io.github.raphonzius.lvc.live.domain.streaming.PublishResult;

/**
 * Outcome counters for one published batch.
 *
 * @param succeeded events acknowledged by the hub
 * @param failed    events that failed (RPC error, deadline, interruption or a {@code rejected} ack)
 */
public record PublishSummary(int succeeded, int failed) {

    public static final PublishSummary EMPTY = new PublishSummary(0, 0);

    public PublishSummary {
        if (succeeded < 0 || failed < 0) {
            throw new IllegalArgumentException("counters must be >= 0: " + succeeded + "/" + failed);
        }
    }

    public int total() {
        return succeeded + failed;
    }

    /** Converts to the domain-level result returned through the streaming ports. */
    public PublishResult toResult() {
        return new PublishResult(succeeded, failed);
    }

    public PublishSummary plus(PublishSummary other) {
        return new PublishSummary(succeeded + other.succeeded, failed + other.failed);
    }
}
