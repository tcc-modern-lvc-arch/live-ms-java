package io.github.raphonzius.lvc.live.infrastructure.input.external.ais;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.raphonzius.lvc.live.domain.vessel.Vessel;
import io.github.raphonzius.lvc.live.domain.vessel.VesselDetails;

import java.util.Objects;

/**
 * Wire DTO for one element of the AIS Friends {@code /vessels/bounding-box} response.
 *
 * <p>Confines the external snake_case JSON format to the AIS adapter. Unknown fields are
 * ignored. Uses {@code com.fasterxml.jackson.annotation} which is shared by Jackson 2 and 3.</p>
 *
 * <p>The fields a position report cannot exist without ({@code id}, {@code latitude},
 * {@code longitude}) are wrappers: a primitive would turn a missing JSON field into {@code 0}
 * and publish the vessel at (0, 0). Check {@link #invalidReason()} before {@link #toDomain()}.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AisVesselResponse(
        @JsonProperty("id") Long id,
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
        @JsonProperty("latitude") Double latitude,
        @JsonProperty("longitude") Double longitude,
        @JsonProperty("lat_grid") Long latGrid,
        @JsonProperty("lon_grid") Long lonGrid
) {

    /** Highest valid true heading in degrees; AIS also uses {@link #HEADING_NOT_AVAILABLE}. */
    static final long MAX_HEADING = 359;

    /** AIS "heading not available" sentinel — carried as no heading. */
    static final long HEADING_NOT_AVAILABLE = 511;

    /** @return why this record cannot become a position report, or {@code null} when it can */
    public String invalidReason() {
        if (id == null) {
            return "missing id";
        }
        if (latitude == null || longitude == null) {
            return "missing latitude/longitude (id=" + id + ")";
        }
        return null;
    }

    /**
     * Translates the wire format into the domain record. Never throws for a record that passed
     * {@link #invalidReason()}: an out-of-range heading becomes {@code null}.
     */
    public Vessel.VesselData toDomain() {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(latitude, "latitude");
        Objects.requireNonNull(longitude, "longitude");
        var details = new VesselDetails(vesselId, vesselClass, imo, name, nameAis, shipTypeId, detailedTypeId,
                timestampOfPosition, length, beam, toBow, toStern, toPort, toStarboard,
                courseOverGround, draught, navigationalStatusId, flag, latGrid, lonGrid);
        return new Vessel.VesselData(
                id,
                mmsi,
                name != null ? name : nameAis,
                "Type_" + Objects.requireNonNullElse(detailedTypeId, 0L),
                latitude,
                longitude,
                Objects.requireNonNullElse(timestampOfPosition, 0L),
                speedOverGround,
                headingOrNull(trueHeading),
                details
        );
    }

    /** 0..359 is a heading; 511 (not available) and anything else is "no heading". */
    private static Integer headingOrNull(Long heading) {
        return heading != null && heading >= 0 && heading <= MAX_HEADING ? Math.toIntExact(heading) : null;
    }
}
