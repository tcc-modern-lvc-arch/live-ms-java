package io.github.raphonzius.lvc.live.infrastructure.scheduler;

import io.github.raphonzius.lvc.live.application.service.CgespService;
import io.github.raphonzius.lvc.live.domain.streaming.PublishResult;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic CGESP flooding polling (today's flooding points, São Paulo time).
 *
 * <p>Interval: {@code cgesp.polling.interval} (default 60 000 ms, fixed delay).</p>
 *
 * <p>Logging, timing, correlation id and error isolation are handled by {@link PollingJobRunner}.</p>
 */
@Component
@RequiredArgsConstructor
public class CgespPollingScheduler implements PollingJob {

    private final PollingJobRunner runner;
    private final CgespService cgespService;

    @Scheduled(fixedDelayString = "${cgesp.polling.interval:60000}")
    public void pollFloodings() {
        runner.run(this);
    }

    @Override
    public String name() {
        return "cgesp";
    }

    @Override
    public PublishResult poll() {
        return cgespService.fetchAndStreamFloodings();
    }
}
