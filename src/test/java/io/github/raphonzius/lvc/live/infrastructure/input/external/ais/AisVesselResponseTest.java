package io.github.raphonzius.lvc.live.infrastructure.input.external.ais;

import io.github.raphonzius.lvc.live.domain.vessel.Vessel;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the AIS Friends wire format (snake_case JSON) is confined to
 * {@link AisVesselResponse} and translated into the Java-named domain record.
 */
class AisVesselResponseTest {

    private static final String JSON = """
            {
              "id": 7,
              "vessel_id": 70,
              "vesselClass": "cargo",
              "imo": 9000001,
              "mmsi": 710000001,
              "name": null,
              "name_ais": "SANTOS EXPRESS",
              "ship_type_id": 70,
              "detailed_type_id": 71,
              "timestamp_of_position": 1700000000,
              "true_heading": 270,
              "course_over_ground": 268.5,
              "speed_over_ground": 12.5,
              "flag": "BR",
              "latitude": -23.9,
              "longitude": -46.3,
              "some_future_field": "ignored"
            }
            """;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void deserializesSnakeCaseWireFormat() {
        var dto = jsonMapper.readValue(JSON, AisVesselResponse.class);

        assertThat(dto.nameAis()).isEqualTo("SANTOS EXPRESS");
        assertThat(dto.detailedTypeId()).isEqualTo(71L);
        assertThat(dto.speedOverGround()).isEqualTo(12.5);
        assertThat(dto.trueHeading()).isEqualTo(270L);
        assertThat(dto.timestampOfPosition()).isEqualTo(1_700_000_000L);
    }

    @Test
    void toDomainUsesJavaNamesAndAisNameFallback() {
        var vessel = jsonMapper.readValue(JSON, AisVesselResponse.class).toDomain();

        assertThat(vessel).isEqualTo(new Vessel.VesselData(
                7L, 710000001L, "SANTOS EXPRESS", "Type_71",
                -23.9, -46.3, 1_700_000_000L, 12.5, 270));
    }

    @Test
    void toDomainPrefersRegisteredNameOverAisName() {
        var dto = new AisVesselResponse(1L, 1L, "N1", "N_AIS", null, null, null, null, 0.0, 0.0);

        assertThat(dto.toDomain().name()).isEqualTo("N1");
    }

    @Test
    void toDomainDefaultsMissingOptionalFields() {
        var dto = new AisVesselResponse(5L, null, null, null, null, null, null, null, -1.0, -2.0);

        assertThat(dto.toDomain()).isEqualTo(new Vessel.VesselData(
                5L, null, null, "Type_0", -1.0, -2.0, 0L, null, null));
    }
}
