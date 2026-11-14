package io.github.raphonzius.lvc.live.infrastructure.scheduler;

import io.github.raphonzius.lvc.live.domain.streaming.PublishResult;
import io.github.raphonzius.lvc.live.infrastructure.observability.CorrelationId;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs {@link PollingJob}s with the cross-cutting concerns every polling cycle shares:
 * <ul>
 *   <li>a correlation id bound for the whole cycle ({@link CorrelationId}, ScopedValue + MDC): a fresh one,
 *       or the enclosing request's when triggered from REST;</li>
 *   <li>start / end logging;</li>
 *   <li>a Micrometer timer {@value #TIMER_NAME} tagged {@code job} and
 *       {@code outcome=success|partial|failed} ({@code partial}: some events failed;
 *       {@code failed}: the cycle threw, or nothing was delivered while something failed);</li>
 *   <li>error isolation — an exception never escapes to the Spring scheduler thread.</li>
 * </ul>
 *
 * <p>Metrics go to the application's {@link MeterRegistry} (Spring Boot Actuator provides one; only
 * {@code health} is web-exposed by default, expose {@code metrics} to read the timer over HTTP).
 * Without a registry bean the runner falls back to {@link Metrics#globalRegistry}, a no-op until a
 * registry is added to it.</p>
 */
@Slf4j
@Component
public class PollingJobRunner {

    /** Timer recording the duration of each polling cycle. */
    public static final String TIMER_NAME = "lvc.polling.duration";

    private final MeterRegistry registry;

    @Autowired
    public PollingJobRunner(ObjectProvider<MeterRegistry> registry) {
        this(registry.getIfAvailable(() -> Metrics.globalRegistry));
    }

    public PollingJobRunner(MeterRegistry registry) {
        this.registry = registry;
    }

    /** Executes one cycle of {@code job}; never throws. */
    public PollingOutcome run(PollingJob job) {
        var outcome = new AtomicReference<PollingOutcome>();
        // A manual poll arrives inside a request that already has an id: keep it, so the cycle logs match the response header.
        var id = CorrelationId.current().orElseGet(CorrelationId::newId);
        CorrelationId.runWith(id, () -> outcome.set(timed(job)));
        return outcome.get();
    }

    private PollingOutcome timed(PollingJob job) {
        log.debug("[{}] polling cycle started", job.name());
        var start = System.nanoTime();
        PollingOutcome outcome;
        try {
            var result = Objects.requireNonNullElse(job.poll(), PublishResult.EMPTY);
            var duration = Duration.ofNanos(System.nanoTime() - start);
            outcome = result.totalFailure() ? new PollingOutcome.Failed(duration, result, null)
                    : result.hasFailures() ? new PollingOutcome.Partial(duration, result)
                    : new PollingOutcome.Succeeded(duration, result);
        } catch (Exception e) {
            outcome = new PollingOutcome.Failed(
                    Duration.ofNanos(System.nanoTime() - start), PublishResult.EMPTY, e);
        }

        // Exhaustive switch over the sealed outcome — record patterns, no default branch.
        var tag = switch (outcome) {
            case PollingOutcome.Succeeded(var duration, var result) -> {
                log.debug("[{}] polling cycle completed in {} ms ({} delivered)",
                        job.name(), duration.toMillis(), result.succeeded());
                yield "success";
            }
            case PollingOutcome.Partial(var duration, var result) -> {
                log.warn("[{}] polling cycle partially failed after {} ms: {} delivered, {} failed",
                        job.name(), duration.toMillis(), result.succeeded(), result.failed());
                yield "partial";
            }
            case PollingOutcome.Failed(var duration, var result, var error) -> {
                if (error != null) {
                    log.error("[{}] polling cycle failed after {} ms", job.name(), duration.toMillis(), error);
                } else {
                    log.error("[{}] polling cycle failed after {} ms: nothing delivered, {} failed",
                            job.name(), duration.toMillis(), result.failed());
                }
                yield "failed";
            }
        };
        Timer.builder(TIMER_NAME)
                .description("Duration of one fetch-and-publish polling cycle")
                .tag("job", job.name())
                .tag("outcome", tag)
                .register(registry)
                .record(outcome.duration());
        return outcome;
    }
}
