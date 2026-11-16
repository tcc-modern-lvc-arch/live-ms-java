package io.github.raphonzius.lvc.live.infrastructure.scheduler;

import io.github.raphonzius.lvc.live.application.service.OlhoVivoService;
import io.github.raphonzius.lvc.live.domain.streaming.PublishResult;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic OlhoVivo (SPTrans) bus position polling for the configured line terms.
 *
 * <p>Interval: {@code olhovivo.polling.interval} (default 60 000 ms, fixed delay).</p>
 *
 * <p>Logging, timing, correlation id and error isolation are handled by {@link PollingJobRunner}.</p>
 */
@Component
@RequiredArgsConstructor
public class OlhoVivoPollingScheduler implements PollingJob {

    private final PollingJobRunner runner;
    private final OlhoVivoService olhoVivoService;

    @Scheduled(fixedDelayString = "${olhovivo.polling.interval:60000}")
    public void pollVehiclePositions() {
        runner.run(this);
    }

    @Override
    public String name() {
        return "olhovivo";
    }

    @Override
    public PublishResult poll() {
        return olhoVivoService.fetchAndStreamVehiclePositions();
    }
}
