package io.github.raphonzius.lvc.live.infrastructure.input.external.ais;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
              "length": 200, "beam": 30, "to_bow": 100, "to_stern": 100, "to_port": 15, "to_starboard": 15,
              "true_heading": 270,
              "course_over_ground": 268.5,
              "speed_over_ground": 12.5,
              "draught": 9.5,
              "navigational_status_id": 0,
              "flag": "BR",
              "latitude": -23.9,
              "longitude": -46.3,
              "lat_grid": 12, "lon_grid": 34,
              "some_future_field": "ignored"
            }
            """;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private AisVesselResponse sample() {
        return jsonMapper.readValue(JSON, AisVesselResponse.class);
    }

    private static AisVesselResponse withHeading(Long heading) {
        return new AisVesselResponse(1L, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, heading, null, null, null, null, null, -1.0, -2.0, null, null);
    }

    @Test
    void deserializesSnakeCaseWireFormat() {
        var dto = sample();

        assertThat(dto.nameAis()).isEqualTo("SANTOS EXPRESS");
        assertThat(dto.detailedTypeId()).isEqualTo(71L);
        assertThat(dto.speedOverGround()).isEqualTo(12.5);
        assertThat(dto.trueHeading()).isEqualTo(270L);
        assertThat(dto.timestampOfPosition()).isEqualTo(1_700_000_000L);
        assertThat(dto.toStarboard()).isEqualTo(15L);
        assertThat(dto.latGrid()).isEqualTo(12L);
    }

    @Test
    void toDomainUsesJavaNamesAndAisNameFallback() {
        var vessel = sample().toDomain();

        assertThat(vessel.id()).isEqualTo(7L);
        assertThat(vessel.mmsi()).isEqualTo(710000001L);
        assertThat(vessel.name()).isEqualTo("SANTOS EXPRESS");
        assertThat(vessel.shipType()).isEqualTo("Type_71");
        assertThat(vessel.latitude()).isEqualTo(-23.9);
        assertThat(vessel.longitude()).isEqualTo(-46.3);
        assertThat(vessel.timestamp()).isEqualTo(1_700_000_000L);
        assertThat(vessel.speedKnots()).isEqualTo(12.5);
        assertThat(vessel.headingDeg()).isEqualTo(270);
    }

    @Test
    void toDomainCarriesDescriptiveDetailsWithRawNames() {
        var details = sample().toDomain().details();

        assertThat(details.name()).as("raw registered name, not the AIS fallback").isNull();
        assertThat(details.nameAis()).isEqualTo("SANTOS EXPRESS");
        assertThat(details.vesselId()).isEqualTo(70L);
        assertThat(details.imo()).isEqualTo(9000001L);
        assertThat(details.flag()).isEqualTo("BR");
        assertThat(details.draught()).isEqualTo(9.5);
        assertThat(details.lonGrid()).isEqualTo(34L);
    }

    @Test
    void toDomainPrefersRegisteredNameOverAisName() {
        var dto = new AisVesselResponse(1L, null, null, null, 1L, "N1", "N_AIS", null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, 0.0, 0.0, null, null);

        assertThat(dto.toDomain().name()).isEqualTo("N1");
    }

    @Test
    void toDomainDefaultsMissingOptionalFields() {
        var vessel = jsonMapper.readValue("{\"id\":5,\"latitude\":-1.0,\"longitude\":-2.0}",
                AisVesselResponse.class).toDomain();

        assertThat(vessel.mmsi()).isNull();
        assertThat(vessel.name()).isNull();
        assertThat(vessel.shipType()).isEqualTo("Type_0");
        assertThat(vessel.timestamp()).isZero();
        assertThat(vessel.speedKnots()).isNull();
        assertThat(vessel.headingDeg()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"latitude\":-1.0,\"longitude\":-2.0}",
            "{\"id\":1,\"longitude\":-2.0}",
            "{\"id\":1,\"latitude\":-1.0}",
            "{\"id\":1}",
            "{}"})
    void recordsWithoutIdLatitudeOrLongitudeAreInvalidInsteadOfDefaultingToZero(String json) {
        assertThat(jsonMapper.readValue(json, AisVesselResponse.class).invalidReason()).isNotNull();
    }

    @Test
    void completeRecordIsValid() {
        assertThat(sample().invalidReason()).isNull();
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 270, 359})
    void validHeadingsAreKept(long heading) {
        assertThat(withHeading(heading).toDomain().headingDeg()).isEqualTo((int) heading);
    }

    @ParameterizedTest
    @ValueSource(longs = {511, 360, -1, 3_000_000_000L, Long.MAX_VALUE})
    void headingOutsideZeroTo359IsNullAndNeverThrows(long heading) {
        assertThat(withHeading(heading).toDomain().headingDeg()).isNull();
    }
}
