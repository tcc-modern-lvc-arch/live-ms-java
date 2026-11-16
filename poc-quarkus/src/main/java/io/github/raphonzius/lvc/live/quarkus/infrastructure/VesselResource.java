package io.github.raphonzius.lvc.live.quarkus.infrastructure;

import io.github.raphonzius.lvc.live.quarkus.application.AisService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

@Path("/api/v1/vessels")
@Produces(MediaType.APPLICATION_JSON)
public class VesselResource {

    private final AisService aisService;

    public VesselResource(AisService aisService) {
        this.aisService = aisService;
    }

    @GET
    @Path("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    /** Triggers one poll cycle synchronously (used by the benchmark). */
    @POST
    @Path("/poll")
    @RunOnVirtualThread
    public Map<String, Integer> poll() {
        return Map.of("vessels", aisService.fetchAndStreamVessels());
    }
}
