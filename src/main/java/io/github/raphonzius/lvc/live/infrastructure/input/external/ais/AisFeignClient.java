package io.github.raphonzius.lvc.live.infrastructure.input.external.ais;

import io.github.raphonzius.lvc.live.domain.vessel.Vessel;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Feign client for AIS Friends API.
 * Provides vessel data from AIS tracking systems.
 *
 * Location: infrastructure/input/external/ais/
 * This is an INPUT ADAPTER - fetches data from external system
 */
@FeignClient(
        name = "ais-api",
        url = "https://www.aisfriends.com",
        configuration = AisFeignConfiguration.class
)
public interface AisFeignClient {

    /**
     * Fetches vessels in a bounding box from the AIS API.
     *
     * @param lonMin minimum longitude
     * @param latMin minimum latitude
     * @param lonMax maximum longitude
     * @param latMax maximum latitude
     * @param zoom zoom level
     * @return list of vessel data
     */
    @GetMapping("/vessels/bounding-box")
    List<Vessel.VesselData> getVessels(
            @RequestParam("lon_min") double lonMin,
            @RequestParam("lat_min") double latMin,
            @RequestParam("lon_max") double lonMax,
            @RequestParam("lat_max") double latMax,
            @RequestParam("zoom") int zoom
    );
}

