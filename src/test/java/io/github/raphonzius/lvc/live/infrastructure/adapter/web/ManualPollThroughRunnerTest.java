package io.github.raphonzius.lvc.live.infrastructure.adapter.web;

import io.github.raphonzius.lvc.live.application.service.AisService;
import io.github.raphonzius.lvc.live.domain.streaming.PublishResult;
import io.github.raphonzius.lvc.live.domain.vessel.AisPort;
import io.github.raphonzius.lvc.live.infrastructure.scheduler.AisPollingScheduler;
import io.github.raphonzius.lvc.live.infrastructure.scheduler.PollingJobRunner;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Manual {@code POST .../poll} runs through {@link PollingJobRunner}: same metrics/logs as the schedule. */
class ManualPollThroughRunnerTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AisService aisService = mock(AisService.class);
    private final VesselController controller = new VesselController(mock(AisPort.class), aisService,
            new PollingJobRunner(registry), new AisPollingScheduler(new PollingJobRunner(registry), aisService));

    @Test
    void manualPollIsTimedLikeAScheduledCycleAndStillAnswers202() {
        when(aisService.fetchAndStreamVessels()).thenReturn(new PublishResult(0, 3));

        var response = controller.manualPoll();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isEqualTo("Polling initiated");
        assertThat(registry.get(PollingJobRunner.TIMER_NAME).tags("job", "ais", "outcome", "failed")
                .timer().count()).isEqualTo(1);
    }

    @Test
    void exceptionsOfTheCycleStillReachTheGlobalExceptionHandler() {
        when(aisService.fetchAndStreamVessels()).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(controller::manualPoll).isInstanceOf(IllegalStateException.class);
        assertThat(registry.get(PollingJobRunner.TIMER_NAME).tags("job", "ais", "outcome", "failed")
                .timer().count()).isEqualTo(1);
    }
}
