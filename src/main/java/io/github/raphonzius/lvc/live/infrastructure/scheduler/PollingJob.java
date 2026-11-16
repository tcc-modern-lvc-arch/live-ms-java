package io.github.raphonzius.lvc.live.infrastructure.scheduler;

import io.github.raphonzius.lvc.live.domain.streaming.PublishResult;

/**
 * One periodic fetch → publish cycle (Strategy).
 *
 * <p>Implementations only say <em>what</em> a cycle does and report what happened as a
 * {@link PublishResult}; {@link PollingJobRunner} owns the cross-cutting concerns (correlation id,
 * logging, timing, outcome metric, error isolation). Each scheduler keeps its own
 * {@code @Scheduled} method and interval property and delegates to the runner (the manual
 * {@code POST .../poll} endpoints go through the runner too, so both paths log and measure alike):</p>
 *
 * <pre>{@code
 * @Scheduled(fixedDelayString = "${ais.polling.interval:30000}")
 * public void pollAisData() { runner.run(this); }
 * }</pre>
 */
public interface PollingJob {

    /** Short, stable job name used as log prefix and metric tag (e.g. {@code ais}). */
    String name();

    /**
     * Executes one cycle. May throw — the runner records and swallows the failure.
     *
     * @return delivered/failed counters; {@code failed > 0} with {@code succeeded == 0} is a failed cycle
     */
    PublishResult poll();
}
