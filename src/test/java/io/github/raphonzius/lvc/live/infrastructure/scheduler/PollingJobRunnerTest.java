package io.github.raphonzius.lvc.live.infrastructure.scheduler;

import io.github.raphonzius.lvc.live.infrastructure.observability.CorrelationId;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PollingJobRunner}: the single place where polling cycles are
 * logged, timed and shielded from exceptions.
 */
class PollingJobRunnerTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final PollingJobRunner runner = new PollingJobRunner(registry);

    private record TestJob(String name, Runnable body) implements PollingJob {
        @Override
        public void poll() {
            body.run();
        }
    }

    @Test
    void successfulCycleIsTimedWithSuccessOutcome() {
        var outcome = runner.run(new TestJob("ais", () -> { }));

        assertThat(outcome).isInstanceOf(PollingOutcome.Succeeded.class);
        var timer = registry.find(PollingJobRunner.TIMER_NAME)
                .tags("job", "ais", "outcome", "success").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    void failingCycleIsSwallowedAndTimedWithFailureOutcome() {
        var boom = new IllegalStateException("upstream down");

        var outcome = runner.run(new TestJob("cgesp", () -> { throw boom; }));

        assertThat(outcome).isInstanceOfSatisfying(PollingOutcome.Failed.class,
                failed -> assertThat(failed.error()).isSameAs(boom));
        assertThat(registry.find(PollingJobRunner.TIMER_NAME)
                .tags("job", "cgesp", "outcome", "failure").timer().count()).isEqualTo(1);
    }

    @Test
    void bindsAFreshCorrelationIdForTheDurationOfTheCycle() {
        var during = new AtomicReference<String>();

        runner.run(new TestJob("olhovivo", () -> during.set(CorrelationId.current().orElse(null))));

        assertThat(during.get()).isNotBlank();
        assertThat(CorrelationId.current()).isEmpty();
    }
}
