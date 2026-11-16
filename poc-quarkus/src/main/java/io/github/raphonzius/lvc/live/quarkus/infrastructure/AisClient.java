package io.github.raphonzius.lvc.live.quarkus.infrastructure;

import io.github.raphonzius.lvc.live.quarkus.domain.Vessel;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

/** Equivalent of AisFeignClient (+ browser headers interceptor). Base URL: quarkus.rest-client.ais-api.url */
@RegisterRestClient(configKey = "ais-api")
@ClientHeaderParam(name = "User-Agent", value = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
@ClientHeaderParam(name = "Accept", value = "application/json, text/plain, */*")
@ClientHeaderParam(name = "Accept-Language", value = "en-US,en;q=0.9")
@ClientHeaderParam(name = "Referer", value = "https://www.aisfriends.com/")
@Path("/vessels/bounding-box")
public interface AisClient {

    @GET
    List<Vessel.VesselData> getVessels(
            @QueryParam("lon_min") double lonMin,
            @QueryParam("lat_min") double latMin,
            @QueryParam("lon_max") double lonMax,
            @QueryParam("lat_max") double latMax,
            @QueryParam("zoom") int zoom);
}
