package io.github.raphonzius.lvc.live.infrastructure.scheduler;

import io.github.raphonzius.lvc.live.infrastructure.observability.CorrelationId;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs {@link PollingJob}s with the cross-cutting concerns every polling cycle shares:
 * <ul>
 *   <li>a fresh correlation id bound for the whole cycle ({@link CorrelationId}, ScopedValue + MDC);</li>
 *   <li>start / end logging;</li>
 *   <li>a Micrometer timer {@value #TIMER_NAME} tagged {@code job} and {@code outcome};</li>
 *   <li>error isolation — an exception never escapes to the Spring scheduler thread.</li>
 * </ul>
 *
 * <p>The application has no actuator, so no {@link MeterRegistry} bean exists by default; the
 * runner then records into {@link Metrics#globalRegistry}, which is a no-op until a registry
 * (e.g. Prometheus via actuator) is added.</p>
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
        CorrelationId.runWith(CorrelationId.newId(), () -> outcome.set(timed(job)));
        return outcome.get();
    }

    private PollingOutcome timed(PollingJob job) {
        log.debug("[{}] polling cycle started", job.name());
        var start = System.nanoTime();
        PollingOutcome outcome;
        try {
            job.poll();
            outcome = new PollingOutcome.Succeeded(Duration.ofNanos(System.nanoTime() - start));
        } catch (Exception e) {
            outcome = new PollingOutcome.Failed(Duration.ofNanos(System.nanoTime() - start), e);
        }

        // Exhaustive switch over the sealed outcome — record patterns, no default branch.
        var tag = switch (outcome) {
            case PollingOutcome.Succeeded(var duration) -> {
                log.debug("[{}] polling cycle completed in {} ms", job.name(), duration.toMillis());
                yield "success";
            }
            case PollingOutcome.Failed(var duration, var error) -> {
                log.error("[{}] polling cycle failed after {} ms", job.name(), duration.toMillis(), error);
                yield "failure";
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
