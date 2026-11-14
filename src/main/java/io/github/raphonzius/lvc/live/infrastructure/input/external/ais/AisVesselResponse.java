package io.github.raphonzius.lvc.live.infrastructure.input.external.ais;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.raphonzius.lvc.live.domain.vessel.Vessel;

import java.util.Objects;

/**
 * Wire DTO for one element of the AIS Friends {@code /vessels/bounding-box} response.
 *
 * <p>Confines the external snake_case JSON format to the AIS adapter. Only the fields the
 * service consumes are bound; everything else in the payload is ignored. Uses
 * {@code com.fasterxml.jackson.annotation} which is shared by Jackson 2 and Jackson 3.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AisVesselResponse(
        @JsonProperty("id") long id,
        @JsonProperty("mmsi") Long mmsi,
        @JsonProperty("name") String name,
        @JsonProperty("name_ais") String nameAis,
        @JsonProperty("detailed_type_id") Long detailedTypeId,
        @JsonProperty("timestamp_of_position") Long timestampOfPosition,
        @JsonProperty("speed_over_ground") Double speedOverGround,
        @JsonProperty("true_heading") Long trueHeading,
        @JsonProperty("latitude") double latitude,
        @JsonProperty("longitude") double longitude
) {

    /** Translates the wire format into the domain record. */
    public Vessel.VesselData toDomain() {
        return new Vessel.VesselData(
                id,
                mmsi,
                name != null ? name : nameAis,
                "Type_" + Objects.requireNonNullElse(detailedTypeId, 0L),
                latitude,
                longitude,
                Objects.requireNonNullElse(timestampOfPosition, 0L),
                speedOverGround,
                trueHeading != null ? Math.toIntExact(trueHeading) : null
        );
    }
}
