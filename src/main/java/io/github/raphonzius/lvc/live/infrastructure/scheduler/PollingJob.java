package io.github.raphonzius.lvc.live.infrastructure.scheduler;

/**
 * One periodic fetch → publish cycle (Strategy).
 *
 * <p>Implementations only say <em>what</em> a cycle does; {@link PollingJobRunner} owns the
 * cross-cutting concerns (correlation id, logging, timing, error isolation). Each scheduler
 * keeps its own {@code @Scheduled} method and interval property and delegates to the runner:</p>
 *
 * <pre>{@code
 * @Scheduled(fixedDelayString = "${ais.polling.interval:30000}")
 * public void pollAisData() { runner.run(this); }
 * }</pre>
 */
public interface PollingJob {

    /** Short, stable job name used as log prefix and metric tag (e.g. {@code ais}). */
    String name();

    /** Executes one cycle. May throw — the runner records and swallows the failure. */
    void poll();
}
