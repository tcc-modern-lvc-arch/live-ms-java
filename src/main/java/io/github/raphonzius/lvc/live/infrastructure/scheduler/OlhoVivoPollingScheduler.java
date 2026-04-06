package io.github.raphonzius.lvc.live.infrastructure.scheduler;

import io.github.raphonzius.lvc.live.application.service.OlhoVivoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler for periodic OlhoVivo vehicle position polling.
 *
 * Polling interval configured in application.yaml: olhovivo.polling.interval
 * Default: 60000ms (60 seconds)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OlhoVivoPollingScheduler {

    private final OlhoVivoService olhoVivoService;

    /**
     * Runs the OlhoVivo polling cycle on a fixed delay.
     * Fetches vehicle positions for all configured line terms and publishes results to {@code buses:stream}.
     * Interval controlled by {@code olhovivo.polling.interval} (default 60 000 ms).
     */
    @Scheduled(fixedDelayString = "${olhovivo.polling.interval:60000}")
    public void pollVehiclePositions() {
        try {
            log.debug("Starting OlhoVivo polling cycle");
            olhoVivoService.fetchAndStreamVehiclePositions();
            log.debug("OlhoVivo polling cycle completed");
        } catch (Exception e) {
            log.error("Error during OlhoVivo polling", e);
        }
    }
}
