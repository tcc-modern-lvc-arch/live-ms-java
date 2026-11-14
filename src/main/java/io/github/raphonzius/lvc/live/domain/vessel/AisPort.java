package io.github.raphonzius.lvc.live.domain.vessel;

import io.github.raphonzius.lvc.live.domain.FetchResult;

import java.util.List;

/**
 * Port interface for AIS vessel data retrieval.
 * Represents the hexagonal architecture boundary.
 */
public interface AisPort {

    /**
     * Fetches vessel data from a bounding box region.
     *
     * @param lonMin minimum longitude
     * @param latMin minimum latitude
     * @param lonMax maximum longitude
     * @param latMax maximum latitude
     * @param zoom zoom level for the query
     * @return vessels in the bounding box (possibly empty), or {@code Unavailable} when the source failed
     */
    FetchResult<List<Vessel.VesselData>> fetchVessels(
            double lonMin,
            double latMin,
            double lonMax,
            double latMax,
            int zoom
    );
}

