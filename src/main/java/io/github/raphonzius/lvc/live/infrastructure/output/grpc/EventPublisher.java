package io.github.raphonzius.lvc.live.infrastructure.output.grpc;

import io.github.raphonzius.lvc.live.infrastructure.observability.CorrelationId;
import io.github.raphonzius.lvc.proto.event.EventRequest;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;

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
 *   <li>Failures are counted and never abort the batch. Per-event failures are logged at DEBUG;
 *       one summary line per batch is logged at INFO, or at WARN with the first failure reason
 *       (e.g. {@code UNAVAILABLE: hub down}) so a hub outage yields one warning, not one per event.</li>
 *   <li>The caller's correlation id is re-bound inside each task so per-event log lines keep it.</li>
 * </ul>
 */
@Slf4j
public final class EventPublisher {

    static final String REJECTED = "rejected";

    private final EventSender sender;
    private final Semaphore permits;
    private final int batchSize;

    public EventPublisher(EventSender sender, int maxConcurrency, int batchSize) {
        if (maxConcurrency < 1 || batchSize < 1) {
            throw new IllegalArgumentException(
                    "maxConcurrency and batchSize must be >= 1, got " + maxConcurrency + "/" + batchSize);
        }
        this.sender = sender;
        this.permits = new Semaphore(maxConcurrency);
        this.batchSize = batchSize;
    }

    /**
     * Publishes all {@code requests}, blocking until every one has completed or failed.
     *
     * @param stream logical stream name for logs (e.g. {@code vessel})
     */
    public PublishSummary publish(String stream, List<EventRequest> requests) {
        if (requests.isEmpty()) {
            log.debug("[{}] nothing to publish", stream);
            return PublishSummary.EMPTY;
        }
        var correlationId = CorrelationId.current().orElseGet(CorrelationId::newId);
        var start = System.nanoTime();

        var failures = requests.stream()
                .gather(Gatherers.windowFixed(batchSize))
                .flatMap(window -> publishWindow(stream, window, correlationId).stream())
                .toList();
        var failed = (int) failures.stream().filter(Optional::isPresent).count();
        var summary = new PublishSummary(requests.size() - failed, failed);

        var elapsedMs = (System.nanoTime() - start) / 1_000_000;
        if (summary.failed() == 0) {
            log.info("[{}] published {}/{} event(s) to EventHub in {} ms",
                    stream, summary.succeeded(), summary.total(), elapsedMs);
        } else {
            var firstReason = failures.stream().flatMap(Optional::stream).findFirst().orElse("unknown");
            log.warn("[{}] published {}/{} event(s) to EventHub in {} ms — {} failed (first: {})",
                    stream, summary.succeeded(), summary.total(), elapsedMs, summary.failed(), firstReason);
        }
        return summary;
    }

    /** @return one entry per event: empty on success, the failure reason otherwise */
    private List<Optional<String>> publishWindow(String stream, List<EventRequest> window, String correlationId) {
        List<Future<Optional<String>>> results;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            results = window.stream()
                    .map(request -> executor.submit(task(stream, request, correlationId)))
                    .toList();
        } // close() waits for every task of the window

        return results.stream().map(Future::resultNow).toList();
    }

    private Callable<Optional<String>> task(String stream, EventRequest request, String correlationId) {
        return () -> {
            var outcome = new AtomicReference<Optional<String>>(Optional.of("not run"));
            CorrelationId.runWith(correlationId, () -> outcome.set(sendOne(stream, request)));
            return Objects.requireNonNull(outcome.get());
        };
    }

    /** @return empty when delivered, otherwise a short failure reason */
    private Optional<String> sendOne(String stream, EventRequest request) {
        try {
            permits.acquire();
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            log.debug("[{}] interrupted before publishing entity={}", stream, request.getEntityId());
            return Optional.of("interrupted");
        }
        try {
            var ack = sender.send(request);
            if (REJECTED.equalsIgnoreCase(ack.getStatus())) {
                log.debug("[{}] EventHub rejected entity={}: {}", stream, request.getEntityId(), ack.getErrorMessage());
                return Optional.of("rejected: " + ack.getErrorMessage());
            }
            log.debug("[{}] published entity={}", stream, request.getEntityId());
            return Optional.empty();
        } catch (StatusRuntimeException e) {
            var reason = describe(e.getStatus());
            log.debug("[{}] failed to publish entity={}: {}", stream, request.getEntityId(), reason);
            return Optional.of(reason);
        } catch (RuntimeException e) {
            // Programming/mapping errors are not expected: keep them loud, with stack trace.
            log.error("[{}] unexpected error publishing entity={}", stream, request.getEntityId(), e);
            return Optional.of(e.getClass().getSimpleName());
        } finally {
            permits.release();
        }
    }

    private static String describe(io.grpc.Status status) {
        return status.getDescription() == null
                ? status.getCode().name()
                : status.getCode() + ": " + status.getDescription();
    }
}
