package io.github.raphonzius.lvc.live.infrastructure.output.grpc;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.raphonzius.lvc.live.infrastructure.observability.CorrelationId;
import io.github.raphonzius.lvc.proto.event.EventAck;
import io.github.raphonzius.lvc.proto.event.EventRequest;
import io.grpc.Status;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link EventPublisher}: bounded-concurrency batch publishing on virtual threads.
 */
class EventPublisherTest {

    private static final EventAck ACCEPTED = EventAck.newBuilder().setStatus("accepted").build();

    private static List<EventRequest> requests(int n) {
        return IntStream.range(0, n)
                .mapToObj(i -> EventRequest.newBuilder().setEntityId("e" + i).build())
                .toList();
    }

    @Test
    void emptyBatchSendsNothing() {
        var calls = new AtomicInteger();
        var publisher = new EventPublisher(_ -> {
            calls.incrementAndGet();
            return ACCEPTED;
        }, 4, 10);

        var summary = publisher.publish("vessel", List.of());

        assertThat(summary).isEqualTo(new PublishSummary(0, 0));
        assertThat(calls).hasValue(0);
    }

    @Test
    void countsEverySuccessfulSendAcrossWindows() {
        var sent = ConcurrentHashMap.<String>newKeySet();
        var publisher = new EventPublisher(r -> {
            sent.add(r.getEntityId());
            return ACCEPTED;
        }, 4, 2);

        var summary = publisher.publish("vessel", requests(5));

        assertThat(summary).isEqualTo(new PublishSummary(5, 0));
        assertThat(sent).containsExactlyInAnyOrder("e0", "e1", "e2", "e3", "e4");
    }

    @Test
    void rpcFailuresAreCountedAndDoNotAbortTheBatch() {
        var publisher = new EventPublisher(r -> {
            if (r.getEntityId().equals("e1")) {
                throw Status.UNAVAILABLE.withDescription("hub down").asRuntimeException();
            }
            return ACCEPTED;
        }, 4, 10);

        var summary = publisher.publish("bus", requests(3));

        assertThat(summary).isEqualTo(new PublishSummary(2, 1));
        assertThat(summary.total()).isEqualTo(3);
    }

    @Test
    void hubOutageLogsOneSummaryWarningWithReasonInsteadOfOnePerEvent() {
        var logger = (Logger) LoggerFactory.getLogger(EventPublisher.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            var publisher = new EventPublisher(_ -> {
                throw Status.UNAVAILABLE.withDescription("hub down")
                        .withCause(new java.net.ConnectException("refused")).asRuntimeException();
            }, 4, 10);

            publisher.publish("vessel", requests(20));
        } finally {
            logger.detachAppender(appender);
        }

        var warnings = appender.list.stream()
                .filter(e -> e.getLevel().isGreaterOrEqual(Level.WARN))
                .toList();
        assertThat(warnings).singleElement().satisfies(e -> {
            assertThat(e.getFormattedMessage()).contains("0/20", "20 failed", "UNAVAILABLE: hub down");
            assertThat(e.getFormattedMessage()).doesNotContain("ConnectException");
        });
    }

    @Test
    void rejectedAckCountsAsFailure() {
        var publisher = new EventPublisher(r -> r.getEntityId().equals("e0")
                ? EventAck.newBuilder().setStatus("rejected").setErrorMessage("bad area").build()
                : ACCEPTED, 4, 10);

        assertThat(publisher.publish("flooding", requests(2))).isEqualTo(new PublishSummary(1, 1));
    }

    @Test
    void neverExceedsMaxConcurrency() {
        var inFlight = new AtomicInteger();
        var peak = new AtomicInteger();
        var publisher = new EventPublisher(_ -> {
            var now = inFlight.incrementAndGet();
            peak.accumulateAndGet(now, Math::max);
            try {
                Thread.sleep(5);
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            } finally {
                inFlight.decrementAndGet();
            }
            return ACCEPTED;
        }, 3, 100);

        var summary = publisher.publish("vessel", requests(40));

        assertThat(summary.succeeded()).isEqualTo(40);
        assertThat(peak.get()).isBetween(1, 3);
    }

    @Test
    void propagatesCorrelationIdToSenderThreads() {
        Set<String> seenScoped = ConcurrentHashMap.newKeySet();
        Set<String> seenMdc = ConcurrentHashMap.newKeySet();
        var publisher = new EventPublisher(_ -> {
            seenScoped.add(CorrelationId.current().orElse("none"));
            seenMdc.add(Objects.requireNonNullElse(MDC.get(CorrelationId.MDC_KEY), "none"));
            return ACCEPTED;
        }, 4, 10);

        CorrelationId.runWith("cid-123", () -> publisher.publish("vessel", requests(5)));

        assertThat(seenScoped).containsExactly("cid-123");
        assertThat(seenMdc).containsExactly("cid-123");
    }
}
