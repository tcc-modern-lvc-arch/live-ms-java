package io.github.raphonzius.lvc.live.infrastructure.scheduler;

import io.github.raphonzius.lvc.live.domain.streaming.PublishResult;
import io.github.raphonzius.lvc.live.infrastructure.observability.CorrelationId;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PollingJobRunner}: the single place where polling cycles are
 * logged, timed and shielded from exceptions.
 */
class PollingJobRunnerTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final PollingJobRunner runner = new PollingJobRunner(registry);

    private record TestJob(String name, Supplier<PublishResult> body) implements PollingJob {
        @Override
        public PublishResult poll() {
            return body.get();
        }
    }

    private long count(String job, String outcome) {
        var timer = registry.find(PollingJobRunner.TIMER_NAME).tags("job", job, "outcome", outcome).timer();
        return timer == null ? 0 : timer.count();
    }

    @Test
    void successfulCycleIsTimedWithSuccessOutcome() {
        runner.run(new TestJob("ais", () -> new PublishResult(3, 0)));

        assertThat(count("ais", "success")).isEqualTo(1);
    }

    @Test
    void emptyCycleIsASuccess() {
        runner.run(new TestJob("ais", () -> PublishResult.EMPTY));

        assertThat(count("ais", "success")).isEqualTo(1);
    }

    @Test
    void someFailedEventsMakeTheCyclePartial() {
        runner.run(new TestJob("ais", () -> new PublishResult(2, 1)));

        assertThat(count("ais", "partial")).isEqualTo(1);
        assertThat(count("ais", "success")).isZero();
    }

    @Test
    void nothingDeliveredWithFailuresIsFailedEvenWithoutException() {
        runner.run(new TestJob("ais", () -> new PublishResult(0, 4)));

        assertThat(count("ais", "failed")).isEqualTo(1);
        assertThat(count("ais", "success")).isZero();
    }

    @Test
    void totalFailureIsLoggedAtErrorLevel() {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(PollingJobRunner.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            runner.run(new TestJob("ais", () -> new PublishResult(0, 4)));
            runner.run(new TestJob("ais", () -> new PublishResult(1, 1)));
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list.stream()
                .filter(e -> e.getLevel().isGreaterOrEqual(ch.qos.logback.classic.Level.WARN))
                .map(e -> e.getLevel()))
                .containsExactly(ch.qos.logback.classic.Level.ERROR, ch.qos.logback.classic.Level.WARN);
    }

    @Test
    void failingCycleIsSwallowedAndTimedWithFailureOutcome() {
        var boom = new IllegalStateException("upstream down");

        assertThatThrownBy(() -> runner.runOrThrow(new TestJob("cgesp", () -> { throw boom; }))).isSameAs(boom);
        assertThat(registry.find(PollingJobRunner.TIMER_NAME)
                .tags("job", "cgesp", "outcome", "failed").timer().count()).isEqualTo(1);
    }

    @Test
    void bindsAFreshCorrelationIdForTheDurationOfTheCycle() {
        var during = new AtomicReference<String>();

        runner.run(new TestJob("olhovivo", () -> {
            during.set(CorrelationId.current().orElse(null));
            return PublishResult.EMPTY;
        }));

        assertThat(during.get()).isNotBlank();
        assertThat(CorrelationId.current()).isEmpty();
    }

    @Test
    void reusesTheCorrelationIdOfTheEnclosingRequest() {
        var during = new AtomicReference<String>();

        CorrelationId.runWith("request-42", () -> runner.run(new TestJob("ais", () -> {
            during.set(CorrelationId.current().orElse(null));
            return PublishResult.EMPTY;
        })));

        assertThat(during.get()).isEqualTo("request-42");
    }
}
