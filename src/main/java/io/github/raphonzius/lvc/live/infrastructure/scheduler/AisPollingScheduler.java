package io.github.raphonzius.lvc.live.infrastructure.scheduler;

import io.github.raphonzius.lvc.live.application.service.AisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler component for periodic AIS data polling.
 * Uses Spring's scheduling mechanism for HTTP polling.
 *
 * Polling interval configured in application.yaml: ais.polling.interval
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AisPollingScheduler {

    private final AisService aisService;

    /**
     * Polls AIS API and streams vessel data at configured interval.
     * Uses fixed delay to ensure requests are not concurrent.
     *
     * Default interval: 30000ms (30 seconds)
     */
    @Scheduled(fixedDelayString = "${ais.polling.interval:30000}")
    public void pollAisData() {
        try {
            log.debug("Starting AIS polling cycle");
            aisService.fetchAndStreamVessels();
            log.debug("AIS polling cycle completed");
        } catch (Exception e) {
            log.error("Error during AIS polling", e);
        }
    }
}

