package io.github.raphonzius.lvc.live.infrastructure.scheduler;

import java.time.Duration;

/**
 * Result of one {@link PollingJob} cycle as recorded by {@link PollingJobRunner}.
 */
public sealed interface PollingOutcome {

    Duration duration();

    /** The cycle completed normally. */
    record Succeeded(Duration duration) implements PollingOutcome {
    }

    /** The cycle threw; the exception was logged and swallowed so the schedule keeps running. */
    record Failed(Duration duration, Exception error) implements PollingOutcome {
    }
}
