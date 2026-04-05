package io.github.raphonzius.lvc.live.infrastructure.input.external.ais;

import io.github.raphonzius.lvc.live.domain.vessel.AisPort;
import io.github.raphonzius.lvc.live.domain.vessel.Vessel;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * AIS adapter implementing the AisPort interface.
 * Provides vessel data from the AIS API with resilience patterns.
 *
 * Location: infrastructure/input/external/ais/
 * This adapter IMPLEMENTS the domain port AisPort.
 * It adapts the AIS API to the domain's expectations.
 *
 * Circuit breaker is configured via ResilienceConfiguration.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AisAdapter implements AisPort {

    private final AisFeignClient aisFeignClient;

    @Override
    @CircuitBreaker(name = "ais-api", fallbackMethod = "fetchVesselsFallback")
    public List<Vessel.VesselData> fetchVessels(
            double lonMin,
            double latMin,
            double lonMax,
            double latMax,
            int zoom
    ) {
        log.info("Fetching vessels from AIS API - Box: ({},{})-({},{}), Zoom: {}",
                lonMin, latMin, lonMax, latMax, zoom);

        var vessels = aisFeignClient.getVessels(lonMin, latMin, lonMax, latMax, zoom);

        if (vessels != null && !vessels.isEmpty()) {
            log.info("Successfully fetched {} vessels from AIS API", vessels.size());
        }

        return vessels;
    }

    /**
     * Fallback method when AIS API is unavailable.
     * Used when circuit breaker is open.
     */
    public List<Vessel.VesselData> fetchVesselsFallback(
            double lonMin,
            double latMin,
            double lonMax,
            double latMax,
            int zoom,
            Exception exception
    ) {
        log.warn("AIS API circuit breaker activated - returning empty list", exception);
        return Collections.emptyList();
    }
}
