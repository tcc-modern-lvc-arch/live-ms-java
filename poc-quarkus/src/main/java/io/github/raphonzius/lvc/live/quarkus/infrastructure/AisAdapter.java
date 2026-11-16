package io.github.raphonzius.lvc.live.quarkus.infrastructure;

import io.github.raphonzius.lvc.live.quarkus.domain.AisPort;
import io.github.raphonzius.lvc.live.quarkus.domain.Vessel;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

/**
 * Mirrors resilience4j "ais-api" (sliding window 10, failure rate 50%, wait 30s open, 3 half-open calls).
 * minimumNumberOfCalls(5) and slowCall* have no SmallRye FT equivalent; requestVolumeThreshold=10 is the window.
 */
@ApplicationScoped
public class AisAdapter implements AisPort {

    private static final Logger LOG = Logger.getLogger(AisAdapter.class);

    private final AisClient client;

    public AisAdapter(@RestClient AisClient client) {
        this.client = client;
    }

    @Override
    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5, delay = 30, delayUnit = ChronoUnit.SECONDS, successThreshold = 3)
    @Fallback(fallbackMethod = "fetchVesselsFallback")
    public List<Vessel.VesselData> fetchVessels(double lonMin, double latMin, double lonMax, double latMax, int zoom) {
        LOG.infof("Fetching vessels from AIS API - Box: (%s,%s)-(%s,%s), Zoom: %d", lonMin, latMin, lonMax, latMax, zoom);
        var vessels = client.getVessels(lonMin, latMin, lonMax, latMax, zoom);
        if (vessels != null && !vessels.isEmpty()) {
            LOG.infof("Successfully fetched %d vessels from AIS API", vessels.size());
        }
        return vessels;
    }

    List<Vessel.VesselData> fetchVesselsFallback(double lonMin, double latMin, double lonMax, double latMax, int zoom) {
        LOG.warn("AIS API unavailable or circuit open - returning empty list");
        return Collections.emptyList();
    }
}
