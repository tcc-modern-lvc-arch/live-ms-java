package io.github.raphonzius.lvc.live.application.service;

import io.github.raphonzius.lvc.live.domain.streaming.VesselStreamingPort;
import io.github.raphonzius.lvc.live.domain.vessel.AisPort;
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
     */
    public void fetchAndStreamVessels() {
        // Brazil coast bounding box
        double lonMin = -49.0000;
        double latMin = -28.5000;
        double lonMax = -35.0000;
        double latMax = -22.5000;
        int zoom = 6;

        fetchAndStreamVessels(lonMin, latMin, lonMax, latMax, zoom);
    }

    /**
     * Fetches vessels from AIS API and publishes them to the streaming channel.
     *
     * @param lonMin minimum longitude
     * @param latMin minimum latitude
     * @param lonMax maximum longitude
     * @param latMax maximum latitude
     * @param zoom   zoom level
     */
    public void fetchAndStreamVessels(
            double lonMin,
            double latMin,
            double lonMax,
            double latMax,
            int zoom
    ) {
        var vessels = aisPort.fetchVessels(lonMin, latMin, lonMax, latMax, zoom);

        if (vessels != null && !vessels.isEmpty()) {
            log.info("Streaming {} vessels to Event Hub", vessels.size());
            vesselStreamingPort.publishVesselBatch(vessels.stream());
        }
    }
}

