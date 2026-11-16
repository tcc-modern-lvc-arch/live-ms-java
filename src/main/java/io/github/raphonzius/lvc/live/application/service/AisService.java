package io.github.raphonzius.lvc.live.application.service;

import io.github.raphonzius.lvc.live.domain.FetchResult;
import io.github.raphonzius.lvc.live.domain.streaming.PublishResult;
import io.github.raphonzius.lvc.live.domain.streaming.VesselStreamingPort;
import io.github.raphonzius.lvc.live.domain.vessel.AisPort;
import io.github.raphonzius.lvc.live.domain.vessel.Vessel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Application service for AIS vessel data orchestration.
 * Coordinates between domain logic and infrastructure.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AisService {

    private final AisPort aisPort;
    private final VesselStreamingPort vesselStreamingPort;

    /**
     * Fetches vessels from AIS API and publishes them to the streaming channel.
     * Uses the Brazil coast bounding box coordinates.
     *
     * @return delivered/failed counters of the cycle
     */
    public PublishResult fetchAndStreamVessels() {
        // Brazil coast bounding box
        double lonMin = -49.0000;
        double latMin = -28.5000;
        double lonMax = -35.0000;
        double latMax = -22.5000;
        int zoom = 6;

        return fetchAndStreamVessels(lonMin, latMin, lonMax, latMax, zoom);
    }

    /**
     * Fetches vessels from AIS API and publishes them to the streaming channel.
     *
     * @param lonMin minimum longitude
     * @param latMin minimum latitude
     * @param lonMax maximum longitude
     * @param latMax maximum latitude
     * @param zoom   zoom level
     * @return delivered/failed counters; an unavailable AIS source counts as one failed unit so the
     *         cycle is reported as failed instead of as "no vessels"
     */
    public PublishResult fetchAndStreamVessels(
            double lonMin,
            double latMin,
            double lonMax,
            double latMax,
            int zoom
    ) {
        return switch (aisPort.fetchVessels(lonMin, latMin, lonMax, latMax, zoom)) {
            case FetchResult.Unavailable<java.util.List<Vessel.VesselData>>(var reason) -> {
                log.warn("AIS source unavailable ({}) — nothing to stream", reason);
                yield PublishResult.failedOf(1);
            }
            case FetchResult.Fetched<java.util.List<Vessel.VesselData>>(var vessels) -> {
                if (vessels == null || vessels.isEmpty()) {
                    yield PublishResult.EMPTY;
                }
                log.info("Streaming {} vessels to Event Hub", vessels.size());
                yield vesselStreamingPort.publishVesselBatch(vessels.stream());
            }
        };
    }
}

