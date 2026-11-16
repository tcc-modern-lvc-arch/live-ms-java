package io.github.raphonzius.lvc.live.quarkus.domain;

import java.util.List;

public interface AisPort {
    List<Vessel.VesselData> fetchVessels(double lonMin, double latMin, double lonMax, double latMax, int zoom);
}
