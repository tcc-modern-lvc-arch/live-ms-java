package io.github.raphonzius.lvc.live.infrastructure.adapter.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.raphonzius.lvc.live.domain.vessel.Vessel;
import io.github.raphonzius.lvc.live.domain.vessel.VesselDetails;

/**
 * REST response element of {@code GET /api/vessels/bounding-box} for API version {@code 1.0}.
 *
 * <p>The JSON keys and types are frozen by the v1.0 contract (they are the old
 * {@code Vessel.VesselData} component names, mixed snake_case and camelCase: {@code vesselClass}
 * is the only camelCase one). Every key is pinned with {@link JsonProperty}, so renaming a Java
 * component or changing the mapper configuration cannot change the wire format — a change here
 * needs a new API version. The domain keeps Java names; this DTO is the only place that knows
 * the legacy ones.</p>
 *
 * <p>Differences with the pre-refactoring payload are limited to values: {@code true_heading}
 * is {@code null} when the provider reports no/unavailable heading (AIS 511) and
 * {@code timestamp_of_position}, {@code name}, {@code name_ais} come from the optional
 * {@link VesselDetails} (all {@code null} when a vessel has none).</p>
 */
public record VesselV1Response(
        @JsonProperty("id") long id,
        @JsonProperty("vessel_id") Long vesselId,
        @JsonProperty("vesselClass") String vesselClass,
        @JsonProperty("imo") Long imo,
        @JsonProperty("mmsi") Long mmsi,
        @JsonProperty("name") String name,
        @JsonProperty("name_ais") String nameAis,
        @JsonProperty("ship_type_id") Long shipTypeId,
        @JsonProperty("detailed_type_id") Long detailedTypeId,
        @JsonProperty("timestamp_of_position") Long timestampOfPosition,
        @JsonProperty("length") Long length,
        @JsonProperty("beam") Long beam,
        @JsonProperty("to_bow") Long toBow,
        @JsonProperty("to_stern") Long toStern,
        @JsonProperty("to_port") Long toPort,
        @JsonProperty("to_starboard") Long toStarboard,
        @JsonProperty("true_heading") Long trueHeading,
        @JsonProperty("course_over_ground") Double courseOverGround,
        @JsonProperty("speed_over_ground") Double speedOverGround,
        @JsonProperty("draught") Double draught,
        @JsonProperty("navigational_status_id") Long navigationalStatusId,
        @JsonProperty("flag") String flag,
        @JsonProperty("latitude") double latitude,
        @JsonProperty("longitude") double longitude,
        @JsonProperty("lat_grid") Long latGrid,
        @JsonProperty("lon_grid") Long lonGrid
) {

    public static VesselV1Response from(Vessel.VesselData vessel) {
        var d = vessel.details();
        if (d == null) {
            d = new VesselDetails(null, null, null, vessel.name(), null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null);
        }
        return new VesselV1Response(
                vessel.id(), d.vesselId(), d.vesselClass(), d.imo(), vessel.mmsi(), d.name(), d.nameAis(),
                d.shipTypeId(), d.detailedTypeId(), d.timestampOfPosition(), d.length(), d.beam(), d.toBow(),
                d.toStern(), d.toPort(), d.toStarboard(),
                vessel.headingDeg() == null ? null : vessel.headingDeg().longValue(),
                d.courseOverGround(), vessel.speedKnots(), d.draught(), d.navigationalStatusId(), d.flag(),
                vessel.latitude(), vessel.longitude(), d.latGrid(), d.lonGrid());
    }
}
