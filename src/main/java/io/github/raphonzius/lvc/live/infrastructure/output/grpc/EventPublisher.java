package io.github.raphonzius.lvc.live.infrastructure.output.grpc;

import io.github.raphonzius.lvc.live.domain.streaming.PublishResult;
import io.github.raphonzius.lvc.live.infrastructure.observability.CorrelationId;
import io.github.raphonzius.lvc.proto.event.EventRequest;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Gatherers;

/**
 * Publishes batches of events concurrently on virtual threads with a global in-flight limit.
 *
 * <ul>
 *   <li>The batch is cut into windows of {@code batchSize} with {@link Gatherers#windowFixed}
 *       (JEP 485), bounding how many tasks/futures exist at once for very large snapshots.</li>
 *   <li>Each event of a window runs on its own virtual thread; a {@link Semaphore} shared by all
 *       streams caps concurrent RPCs to the hub at {@code maxConcurrency}.</li>
 *   <li>Success means the hub answered {@code "accepted"} (same contract as virtual-ms and the
 *       hub itself); any other ack status, an RPC error or a task that ended abnormally is a failure.</li>
 *   <li>Failures are counted and never abort the batch — except <em>fail-fast</em>: when a whole
 *       window fails and every failure is {@code UNAVAILABLE} / {@code DEADLINE_EXCEEDED}, the hub
 *       is considered down and the remaining windows are not sent (counted as failed, reason
 *       {@value #SKIPPED}). Without it a hub outage would cost one full deadline per event.</li>
 *   <li>Per-event failures are logged at DEBUG; one summary line per batch is logged at INFO,
 *       WARN (partial failure) or ERROR (nothing delivered), with the first failure reason.</li>
 *   <li>Counter {@value #METRIC} (tags {@code stream}, {@code result=accepted|failed}) tracks events.</li>
 *   <li>The caller's correlation id is re-bound inside each task so per-event log lines keep it.</li>
 * </ul>
 */
@Slf4j
public final class EventPublisher {

    static final String ACCEPTED = "accepted";
    static final String SKIPPED = "skipped: event-hub unavailable";

    /** Counter of published events, tagged {@code stream} and {@code result}. */
    public static final String METRIC = "lvc.events.published";

    /** One failed event: why, and whether it says the hub itself is unreachable. */
    private record Failure(String reason, boolean hubUnavailable) {
    }

    private final EventSender sender;
    private final Semaphore permits;
    private final int batchSize;
    private final MeterRegistry registry;

    public EventPublisher(EventSender sender, int maxConcurrency, int batchSize) {
        this(sender, maxConcurrency, batchSize, Metrics.globalRegistry);
    }

    public EventPublisher(EventSender sender, int maxConcurrency, int batchSize, MeterRegistry registry) {
        if (maxConcurrency < 1 || batchSize < 1) {
            throw new IllegalArgumentException(
                    "maxConcurrency and batchSize must be >= 1, got " + maxConcurrency + "/" + batchSize);
        }
        this.sender = sender;
        this.permits = new Semaphore(maxConcurrency);
        this.batchSize = batchSize;
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    /**
     * Publishes all {@code requests}, blocking until every one has completed or failed.
     *
     * @param stream logical stream name for logs and metrics (e.g. {@code vessel})
     */
    public PublishResult publish(String stream, List<EventRequest> requests) {
        if (requests.isEmpty()) {
            log.debug("[{}] nothing to publish", stream);
            return PublishResult.EMPTY;
        }
        var correlationId = CorrelationId.current().orElseGet(CorrelationId::newId);
        var start = System.nanoTime();

        var failures = new ArrayList<Failure>();
        var skipped = 0;
        var hubDown = false;
        var windows = requests.stream().gather(Gatherers.windowFixed(batchSize)).iterator();
        while (windows.hasNext()) {
            var window = windows.next();
            if (hubDown) {
                skipped += window.size();
                continue;
            }
            var results = publishWindow(stream, window, correlationId);
            var windowFailures = results.stream().flatMap(Optional::stream).toList();
            failures.addAll(windowFailures);
            hubDown = windowFailures.size() == window.size()
                    && windowFailures.stream().allMatch(Failure::hubUnavailable);
        }

        var failed = failures.size() + skipped;
        var summary = new PublishResult(requests.size() - failed, failed);
        record(stream, summary);
        logSummary(stream, summary, failures, skipped, (System.nanoTime() - start) / 1_000_000);
        return summary;
    }

    private void record(String stream, PublishResult summary) {
        registry.counter(METRIC, "stream", stream, "result", "accepted").increment(summary.succeeded());
        registry.counter(METRIC, "stream", stream, "result", "failed").increment(summary.failed());
    }

    private static void logSummary(String stream, PublishResult summary, List<Failure> failures,
                                   int skipped, long elapsedMs) {
        if (summary.failed() == 0) {
            log.info("[{}] published {}/{} event(s) to EventHub in {} ms",
                    stream, summary.succeeded(), summary.total(), elapsedMs);
            return;
        }
        var firstReason = failures.stream().map(Failure::reason).findFirst().orElse(SKIPPED);
        var skippedNote = skipped > 0 ? ", " + skipped + " " + SKIPPED : "";
        if (summary.succeeded() == 0) {
            log.error("[{}] published {}/{} event(s) to EventHub in {} ms — {} failed (first: {}{})",
                    stream, summary.succeeded(), summary.total(), elapsedMs, summary.failed(), firstReason,
                    skippedNote);
        } else {
            log.warn("[{}] published {}/{} event(s) to EventHub in {} ms — {} failed (first: {}{})",
                    stream, summary.succeeded(), summary.total(), elapsedMs, summary.failed(), firstReason,
                    skippedNote);
        }
    }

    /** @return one entry per event: empty on success, the failure otherwise */
    private List<Optional<Failure>> publishWindow(String stream, List<EventRequest> window, String correlationId) {
        List<Future<Optional<Failure>>> results;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            results = window.stream()
                    .map(request -> executor.submit(task(stream, request, correlationId)))
                    .toList();
        } // close() waits for every task of the window

        // resultNow() throws for a task that ended with an Error (or was cancelled): check the state.
        return results.stream().map(EventPublisher::outcomeOf).toList();
    }

    private static Optional<Failure> outcomeOf(Future<Optional<Failure>> future) {
        return switch (future.state()) {
            case SUCCESS -> future.resultNow();
            case FAILED -> {
                var cause = future.exceptionNow();
                log.error("publishing task ended abnormally", cause);
                yield Optional.of(new Failure("task failed: " + cause.getClass().getSimpleName(), false));
            }
            case CANCELLED, RUNNING -> Optional.of(new Failure("task " + future.state().name().toLowerCase(), false));
        };
    }

    private Callable<Optional<Failure>> task(String stream, EventRequest request, String correlationId) {
        return () -> {
            var outcome = new AtomicReference<Optional<Failure>>(Optional.of(new Failure("not run", false)));
            CorrelationId.runWith(correlationId, () -> outcome.set(sendOne(stream, request)));
            return Objects.requireNonNull(outcome.get());
        };
    }

    /** @return empty when delivered, otherwise the failure */
    private Optional<Failure> sendOne(String stream, EventRequest request) {
        try {
            permits.acquire();
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            log.debug("[{}] interrupted before publishing entity={}", stream, request.getEntityId());
            return Optional.of(new Failure("interrupted", false));
        }
        try {
            var ack = sender.send(request);
            if (!ACCEPTED.equalsIgnoreCase(ack.getStatus())) {
                log.debug("[{}] EventHub did not accept entity={}: status='{}' {}",
                        stream, request.getEntityId(), ack.getStatus(), ack.getErrorMessage());
                var status = ack.getStatus().isEmpty() ? "no status" : ack.getStatus();
                return Optional.of(new Failure(status + ": " + ack.getErrorMessage(), false));
            }
            log.debug("[{}] published entity={}", stream, request.getEntityId());
            return Optional.empty();
        } catch (StatusRuntimeException e) {
            var reason = describe(e.getStatus());
            log.debug("[{}] failed to publish entity={}: {}", stream, request.getEntityId(), reason);
            var code = e.getStatus().getCode();
            return Optional.of(new Failure(reason,
                    code == Status.Code.UNAVAILABLE || code == Status.Code.DEADLINE_EXCEEDED));
        } catch (RuntimeException e) {
            // Programming/mapping errors are not expected: keep them loud, with stack trace.
            log.error("[{}] unexpected error publishing entity={}", stream, request.getEntityId(), e);
            return Optional.of(new Failure(e.getClass().getSimpleName(), false));
        } finally {
            permits.release();
        }
    }

    private static String describe(Status status) {
        return status.getDescription() == null
                ? status.getCode().name()
                : status.getCode() + ": " + status.getDescription();
    }
}
