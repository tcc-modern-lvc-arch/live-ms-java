package io.github.raphonzius.lvc.live.infrastructure.adapter.web;

import io.github.raphonzius.lvc.live.application.service.AisService;
import io.github.raphonzius.lvc.live.domain.FetchResult;
import io.github.raphonzius.lvc.live.domain.vessel.AisPort;
import io.github.raphonzius.lvc.live.domain.vessel.Vessel;
import io.github.raphonzius.lvc.live.domain.vessel.VesselDetails;
import io.github.raphonzius.lvc.live.infrastructure.adapter.web.dto.VesselV1Response;
import io.github.raphonzius.lvc.live.infrastructure.scheduler.AisPollingScheduler;
import io.github.raphonzius.lvc.live.infrastructure.scheduler.PollingJobRunner;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Contract test for {@code GET /api/vessels/bounding-box} v1.0: the JSON keys are the ones the
 * API shipped with ({@code git show 3e848ff:.../Vessel.java}, record components in order).
 */
class VesselV1ContractTest {

    /** Keys of the v1.0 payload, in declaration order of the original record. */
    private static final List<String> V1_KEYS = List.of(
            "id", "vessel_id", "vesselClass", "imo", "mmsi", "name", "name_ais", "ship_type_id",
            "detailed_type_id", "timestamp_of_position", "length", "beam", "to_bow", "to_stern", "to_port",
            "to_starboard", "true_heading", "course_over_ground", "speed_over_ground", "draught",
            "navigational_status_id", "flag", "latitude", "longitude", "lat_grid", "lon_grid");

    private final JsonMapper mapper = JsonMapper.builder().build();

    private static Vessel.VesselData full() {
        var details = new VesselDetails(70L, "cargo", 9000001L, "REG NAME", "AIS NAME", 70L, 71L,
                1_700_000_000L, 200L, 30L, 100L, 100L, 15L, 15L, 268.5, 9.5, 0L, "BR", 12L, 34L);
        return new Vessel.VesselData(7L, 710000001L, "REG NAME", "Type_71", -23.9, -46.3,
                1_700_000_000L, 12.5, 270, details);
    }

    private static List<String> keys(JsonNode node) {
        var keys = new ArrayList<String>();
        node.propertyNames().forEach(keys::add);
        return keys;
    }

    @Test
    void serializedKeysAreExactlyTheV1Contract() {
        var json = mapper.valueToTree(VesselV1Response.from(full()));

        assertThat(keys(json)).containsExactlyElementsOf(V1_KEYS);
    }

    @Test
    void valuesMapToTheLegacyFields() {
        var json = mapper.valueToTree(VesselV1Response.from(full()));

        assertThat(json.get("id").asLong()).isEqualTo(7L);
        assertThat(json.get("vessel_id").asLong()).isEqualTo(70L);
        assertThat(json.get("vesselClass").asString()).isEqualTo("cargo");
        assertThat(json.get("name").asString()).isEqualTo("REG NAME");
        assertThat(json.get("name_ais").asString()).isEqualTo("AIS NAME");
        assertThat(json.get("detailed_type_id").asLong()).isEqualTo(71L);
        assertThat(json.get("timestamp_of_position").asLong()).isEqualTo(1_700_000_000L);
        assertThat(json.get("true_heading").asLong()).isEqualTo(270L);
        assertThat(json.get("speed_over_ground").asDouble()).isEqualTo(12.5);
        assertThat(json.get("course_over_ground").asDouble()).isEqualTo(268.5);
        assertThat(json.get("latitude").asDouble()).isEqualTo(-23.9);
        assertThat(json.get("lon_grid").asLong()).isEqualTo(34L);
    }

    @Test
    void vesselWithoutDetailsStillSerializesAllKeys() {
        var bare = new Vessel.VesselData(5L, null, "N", "Type_0", -1.0, -2.0, 0L, null, null);

        var json = mapper.valueToTree(VesselV1Response.from(bare));

        assertThat(keys(json)).containsExactlyElementsOf(V1_KEYS);
        assertThat(json.get("name").asString()).isEqualTo("N");
        assertThat(json.get("vessel_id").isNull()).isTrue();
        assertThat(json.get("true_heading").isNull()).isTrue();
    }

    @Test
    void controllerReturnsTheV1Dto() {
        var aisPort = mock(AisPort.class);
        when(aisPort.fetchVessels(-49.0, -28.5, -35.0, -22.5, 6))
                .thenReturn(FetchResult.of(List.of(full())));
        var controller = new VesselController(aisPort, mock(AisService.class),
                new PollingJobRunner(new SimpleMeterRegistry()), mock(AisPollingScheduler.class));

        var body = controller.getVessels(-49.0, -28.5, -35.0, -22.5, 6).getBody();

        assertThat(body).singleElement().isInstanceOf(VesselV1Response.class);
        assertThat(keys(mapper.valueToTree(body.getFirst()))).containsExactlyElementsOf(V1_KEYS);
    }

    @Test
    void controllerKeepsReturningAnEmptyListWhenAisIsUnavailable() {
        var aisPort = mock(AisPort.class);
        when(aisPort.fetchVessels(-49.0, -28.5, -35.0, -22.5, 6)).thenReturn(FetchResult.unavailable("down"));
        var controller = new VesselController(aisPort, mock(AisService.class),
                new PollingJobRunner(new SimpleMeterRegistry()), mock(AisPollingScheduler.class));

        assertThat(controller.getVessels(-49.0, -28.5, -35.0, -22.5, 6).getBody()).isEmpty();
    }
}
