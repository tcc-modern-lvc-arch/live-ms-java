package io.github.raphonzius.lvc.live.infrastructure.scheduler;

import io.github.raphonzius.lvc.live.application.service.AisService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic AIS vessel polling.
 *
 * <p>Interval: {@code ais.polling.interval} (default 30 000 ms, fixed delay so cycles never overlap).</p>
 *
 * <p>Logging, timing, correlation id and error isolation are handled by {@link PollingJobRunner}.</p>
 */
@Component
@RequiredArgsConstructor
public class AisPollingScheduler implements PollingJob {

    private final PollingJobRunner runner;
    private final AisService aisService;

    @Scheduled(fixedDelayString = "${ais.polling.interval:30000}")
    public void pollAisData() {
        runner.run(this);
    }

    @Override
    public String name() {
        return "ais";
    }

    @Override
    public void poll() {
        aisService.fetchAndStreamVessels();
    }
}
