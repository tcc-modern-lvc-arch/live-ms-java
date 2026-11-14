package io.github.raphonzius.lvc.live.infrastructure.scheduler;

import io.github.raphonzius.lvc.live.domain.streaming.PublishResult;

import java.time.Duration;

/**
 * Result of one {@link PollingJob} cycle as recorded by {@link PollingJobRunner}.
 */
public sealed interface PollingOutcome {

    Duration duration();

    /** What the cycle delivered (empty when it threw before publishing). */
    PublishResult result();

    /** Everything the cycle tried to deliver was delivered (or there was nothing to deliver). */
    record Succeeded(Duration duration, PublishResult result) implements PollingOutcome {
    }

    /** Some units were delivered and some failed. */
    record Partial(Duration duration, PublishResult result) implements PollingOutcome {
    }

    /**
     * Nothing was delivered: either the cycle threw ({@code error} set; logged and swallowed so the
     * schedule keeps running) or every unit failed ({@code error} is {@code null}).
     */
    record Failed(Duration duration, PublishResult result, Exception error) implements PollingOutcome {
    }

    /** Rethrows the cycle's exception, if it threw one, so callers that report to a client can map it. */
    default void rethrowIfThrown() {
        if (this instanceof Failed(var _, var _, var error) && error instanceof RuntimeException runtime) {
            throw runtime;
        }
    }
}
