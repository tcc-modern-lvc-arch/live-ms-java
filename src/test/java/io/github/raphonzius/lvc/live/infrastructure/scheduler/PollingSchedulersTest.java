package io.github.raphonzius.lvc.live.infrastructure.scheduler;

import io.github.raphonzius.lvc.live.application.service.AisService;
import io.github.raphonzius.lvc.live.application.service.CgespService;
import io.github.raphonzius.lvc.live.application.service.OlhoVivoService;
import io.github.raphonzius.lvc.live.domain.streaming.PublishResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The three schedulers are thin {@link PollingJob} strategies: each delegates its cycle to
 * {@link PollingJobRunner}, which calls the matching application service.
 */
@ExtendWith(MockitoExtension.class)
class PollingSchedulersTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final PollingJobRunner runner = new PollingJobRunner(registry);

    @Mock
    private AisService aisService;
    @Mock
    private CgespService cgespService;
    @Mock
    private OlhoVivoService olhoVivoService;

    private long cycles(String job) {
        return registry.find(PollingJobRunner.TIMER_NAME).tag("job", job).timers().stream()
                .mapToLong(t -> t.count()).sum();
    }

    @Test
    void aisSchedulerRunsVesselCycleThroughRunner() {
        var scheduler = new AisPollingScheduler(runner, aisService);

        when(aisService.fetchAndStreamVessels()).thenReturn(new PublishResult(1, 0));

        scheduler.pollAisData();

        verify(aisService).fetchAndStreamVessels();
        assertThat(scheduler.name()).isEqualTo("ais");
        assertThat(cycles("ais")).isEqualTo(1);
    }

    @Test
    void cgespSchedulerSwallowsServiceFailure() {
        when(cgespService.fetchAndStreamFloodings()).thenThrow(new RuntimeException("scrape failed"));
        var scheduler = new CgespPollingScheduler(runner, cgespService);

        assertThatNoException().isThrownBy(scheduler::pollFloodings);
        assertThat(cycles("cgesp")).isEqualTo(1);
    }

    @Test
    void olhoVivoSchedulerRunsBusCycleThroughRunner() {
        var scheduler = new OlhoVivoPollingScheduler(runner, olhoVivoService);

        scheduler.pollVehiclePositions();

        verify(olhoVivoService).fetchAndStreamVehiclePositions();
        assertThat(cycles("olhovivo")).isEqualTo(1);
    }
}
