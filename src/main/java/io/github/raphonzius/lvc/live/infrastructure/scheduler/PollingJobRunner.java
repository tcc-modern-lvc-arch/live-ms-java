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

    /** Executes one cycle of {@code job}; never throws (failures are logged and timed). */
    public void run(PollingJob job) {
        try {
            runOrThrow(job);
        } catch (RuntimeException _) {
            // already logged and timed; the schedule must keep running
        }
    }

    /** Like {@link #run}, but rethrows the cycle's exception so a REST caller can map it. */
    public void runOrThrow(PollingJob job) {
        var failure = new AtomicReference<RuntimeException>();
        // A manual poll arrives inside a request that already has an id: keep it, so the cycle logs match the response header.
        var id = CorrelationId.current().orElseGet(CorrelationId::newId);
        CorrelationId.runWith(id, () -> {
            try {
                timed(job);
            } catch (RuntimeException e) {
                failure.set(e);
            }
        });
        if (failure.get() != null) {
            throw failure.get();
        }
    }

    private void timed(PollingJob job) {
        log.debug("[{}] polling cycle started", job.name());
        var start = System.nanoTime();
        PublishResult result;
        try {
            result = Objects.requireNonNullElse(job.poll(), PublishResult.EMPTY);
        } catch (RuntimeException e) {
            var elapsed = Duration.ofNanos(System.nanoTime() - start);
            log.error("[{}] polling cycle failed after {} ms", job.name(), elapsed.toMillis(), e);
            record(job, "failed", elapsed);
            throw e;
        }
        var elapsed = Duration.ofNanos(System.nanoTime() - start);
        if (result.totalFailure()) {
            log.error("[{}] polling cycle failed after {} ms: nothing delivered, {} failed",
                    job.name(), elapsed.toMillis(), result.failed());
            record(job, "failed", elapsed);
        } else if (result.hasFailures()) {
            log.warn("[{}] polling cycle partially failed after {} ms: {} delivered, {} failed",
                    job.name(), elapsed.toMillis(), result.succeeded(), result.failed());
            record(job, "partial", elapsed);
        } else {
            log.debug("[{}] polling cycle completed in {} ms ({} delivered)",
                    job.name(), elapsed.toMillis(), result.succeeded());
            record(job, "success", elapsed);
        }
    }

    private void record(PollingJob job, String outcome, Duration elapsed) {
        Timer.builder(TIMER_NAME)
                .description("Duration of one fetch-and-publish polling cycle")
                .tag("job", job.name())
                .tag("outcome", outcome)
                .register(registry)
                .record(elapsed);
    }
}
