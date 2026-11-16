package io.github.raphonzius.lvc.live.quarkus.infrastructure;

import io.github.raphonzius.lvc.live.quarkus.application.AisService;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

@ApplicationScoped
public class AisPollingScheduler {

    private static final Logger LOG = Logger.getLogger(AisPollingScheduler.class);

    private final AisService aisService;

    public AisPollingScheduler(AisService aisService) {
        this.aisService = aisService;
    }

    @Scheduled(every = "${ais.polling.interval}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    @RunOnVirtualThread
    void pollAisData() {
        try {
            LOG.debug("Starting AIS polling cycle");
            aisService.fetchAndStreamVessels();
            LOG.debug("AIS polling cycle completed");
        } catch (Exception e) {
            LOG.error("Error during AIS polling", e);
        }
    }
}
