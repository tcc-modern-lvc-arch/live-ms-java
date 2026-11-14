package io.github.raphonzius.lvc.live.infrastructure.output.grpc;

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

    public PublishSummary plus(PublishSummary other) {
        return new PublishSummary(succeeded + other.succeeded, failed + other.failed);
    }
}
