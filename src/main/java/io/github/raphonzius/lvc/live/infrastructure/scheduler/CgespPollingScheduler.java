package io.github.raphonzius.lvc.live.infrastructure.scheduler;

import io.github.raphonzius.lvc.live.application.service.CgespService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler for periodic CGESP flooding data polling.
 *
 * <p>Polling interval configured in application.yaml: {@code cgesp.polling.interval}
 * Default: 60000 ms (1 minute)</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CgespPollingScheduler {

    private final CgespService cgespService;

    /**
     * Runs the CGESP polling cycle on a fixed delay.
     * Fetches today's flooding points and publishes results to {@code floodings:stream}.
     * Interval controlled by {@code cgesp.polling.interval} (default 60 000 ms).
     */
    @Scheduled(fixedDelayString = "${cgesp.polling.interval:60000}")
    public void pollFloodings() {
        try {
            log.debug("Starting CGESP flooding polling cycle");
            cgespService.fetchAndStreamFloodings();
            log.debug("CGESP flooding polling cycle completed");
        } catch (Exception e) {
            log.error("Error during CGESP flooding polling", e);
        }
    }
}
