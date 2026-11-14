package io.github.raphonzius.lvc.live.infrastructure.scheduler;

import io.github.raphonzius.lvc.live.domain.streaming.PublishResult;
import io.github.raphonzius.lvc.live.infrastructure.observability.CorrelationId;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

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
        var outcome = runner.run(new TestJob("ais", () -> new PublishResult(3, 0)));

        assertThat(outcome).isInstanceOf(PollingOutcome.Succeeded.class);
        assertThat(count("ais", "success")).isEqualTo(1);
    }

    @Test
    void emptyCycleIsASuccess() {
        var outcome = runner.run(new TestJob("ais", () -> PublishResult.EMPTY));

        assertThat(outcome).isInstanceOf(PollingOutcome.Succeeded.class);
        assertThat(count("ais", "success")).isEqualTo(1);
    }

    @Test
    void someFailedEventsMakeTheCyclePartial() {
        var outcome = runner.run(new TestJob("ais", () -> new PublishResult(2, 1)));

        assertThat(outcome).isInstanceOfSatisfying(PollingOutcome.Partial.class,
                partial -> assertThat(partial.result()).isEqualTo(new PublishResult(2, 1)));
        assertThat(count("ais", "partial")).isEqualTo(1);
        assertThat(count("ais", "success")).isZero();
    }

    @Test
    void nothingDeliveredWithFailuresIsFailedEvenWithoutException() {
        var outcome = runner.run(new TestJob("ais", () -> new PublishResult(0, 4)));

        assertThat(outcome).isInstanceOfSatisfying(PollingOutcome.Failed.class, failed -> {
            assertThat(failed.error()).isNull();
            assertThat(failed.result()).isEqualTo(new PublishResult(0, 4));
        });
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

        var outcome = runner.run(new TestJob("cgesp", () -> { throw boom; }));

        assertThat(outcome).isInstanceOfSatisfying(PollingOutcome.Failed.class,
                failed -> assertThat(failed.error()).isSameAs(boom));
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
