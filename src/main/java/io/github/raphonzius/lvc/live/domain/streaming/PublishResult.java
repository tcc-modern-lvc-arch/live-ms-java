package io.github.raphonzius.lvc.live.domain.streaming;

/**
 * Domain-level outcome of publishing a batch to a streaming channel.
 *
 * <p>{@code failed} counts units of work that did not make it downstream: events that were not
 * acknowledged (RPC error, deadline, rejection, mapping failure, skipped after a fail-fast abort)
 * and, for orchestration services, upstream fetches that were unavailable and therefore produced
 * nothing to publish.</p>
 *
 * @param succeeded units delivered
 * @param failed    units that failed
 */
public record PublishResult(int succeeded, int failed) {

    public static final PublishResult EMPTY = new PublishResult(0, 0);

    public PublishResult {
        if (succeeded < 0 || failed < 0) {
            throw new IllegalArgumentException("counters must be >= 0: " + succeeded + "/" + failed);
        }
    }

    /** A result made of {@code count} failed units and nothing delivered. */
    public static PublishResult failedOf(int count) {
        return new PublishResult(0, count);
    }

    public int total() {
        return succeeded + failed;
    }

    public PublishResult plus(PublishResult other) {
        return new PublishResult(succeeded + other.succeeded, failed + other.failed);
    }

    /** {@code true} when at least one unit failed. */
    public boolean hasFailures() {
        return failed > 0;
    }

    /** {@code true} when units failed and none succeeded. */
    public boolean totalFailure() {
        return succeeded == 0 && failed > 0;
    }
}
