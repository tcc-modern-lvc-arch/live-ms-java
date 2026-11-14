package io.github.raphonzius.lvc.live.quarkus.application;

import io.github.raphonzius.lvc.live.quarkus.domain.AisPort;
import io.github.raphonzius.lvc.live.quarkus.domain.VesselStreamingPort;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

@ApplicationScoped
public class AisService {

    private static final Logger LOG = Logger.getLogger(AisService.class);

    private final AisPort aisPort;
    private final VesselStreamingPort streamingPort;

    public AisService(AisPort aisPort, VesselStreamingPort streamingPort) {
        this.aisPort = aisPort;
        this.streamingPort = streamingPort;
    }

    /** Brazil coast bounding box, same as the Spring slice. Returns number of vessels fetched. */
    public int fetchAndStreamVessels() {
        return fetchAndStreamVessels(-49.0, -28.5, -35.0, -22.5, 6);
    }

    public int fetchAndStreamVessels(double lonMin, double latMin, double lonMax, double latMax, int zoom) {
        var vessels = aisPort.fetchVessels(lonMin, latMin, lonMax, latMax, zoom);
        if (vessels != null && !vessels.isEmpty()) {
            LOG.infof("Streaming %d vessels to Event Hub", vessels.size());
            streamingPort.publishVesselBatch(vessels.stream());
            return vessels.size();
        }
        return 0;
    }
}
