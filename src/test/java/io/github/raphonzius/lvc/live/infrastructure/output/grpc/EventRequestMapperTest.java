package io.github.raphonzius.lvc.live.infrastructure.output.grpc;

import io.github.raphonzius.lvc.live.domain.bus.BusVehicle;
import io.github.raphonzius.lvc.live.domain.bus.VehiclePosition;
import io.github.raphonzius.lvc.live.domain.flooding.FloodingPoint;
import io.github.raphonzius.lvc.live.domain.vessel.Vessel;
import io.github.raphonzius.lvc.proto.event.EntityType;
import io.github.raphonzius.lvc.proto.event.EventKind;
import io.github.raphonzius.lvc.proto.event.EventRequest;
import io.github.raphonzius.lvc.proto.event.LvcOrigin;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link EventRequestMapper} — the anti-corruption layer between the domain
 * model and the event-hub protobuf contract. Pure: no gRPC, no Spring.
 */
class EventRequestMapperTest {

    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");
    private static final String AREA = "area-42";

    private final EventRequestMapper mapper =
            new EventRequestMapper(AREA, Clock.fixed(NOW, ZoneOffset.UTC));

    private static void assertEnvelope(EventRequest request, String source, EntityType type) {
        assertThat(request.getAreaId()).isEqualTo(AREA);
        assertThat(request.getSource()).isEqualTo(source);
        assertThat(request.getEntityType()).isEqualTo(type);
        assertThat(request.getEventKind()).isEqualTo(EventKind.MOVE);
        assertThat(request.getLvc()).isEqualTo(LvcOrigin.LIVE);
        assertThat(request.getTimestampMs()).isEqualTo(NOW.toEpochMilli());
    }

    @Nested
    class VesselMapping {

        @Test
        void mapsAllFieldsWhenPresent() {
            var vessel = new Vessel.VesselData(1L, 710000001L, "SANTOS EXPRESS", "Type_71",
                    -23.9, -46.3, 1_700_000_000L, 12.5, 270);

            var request = mapper.toEvent(vessel);

            assertEnvelope(request, "ais", EntityType.VESSEL);
            assertThat(request.getEntityId()).isEqualTo("710000001");
            var payload = request.getVessel();
            assertThat(payload.getLocation().getLat()).isEqualTo(-23.9);
            assertThat(payload.getLocation().getLon()).isEqualTo(-46.3);
            assertThat(payload.getMmsi()).isEqualTo("710000001");
            assertThat(payload.getVesselName()).isEqualTo("SANTOS EXPRESS");
            assertThat(payload.getSpeedKnots()).isEqualTo(12.5f);
            assertThat(payload.getHeadingDeg()).isEqualTo(270f);
            assertThat(payload.getShipType()).isEqualTo("Type_71");
        }

        @Test
        void fallsBackToIdAndLeavesOptionalFieldsUnsetWhenNull() {
            var vessel = new Vessel.VesselData(99L, null, null, null,
                    -23.9, -46.3, 0L, null, null);

            var request = mapper.toEvent(vessel);

            assertThat(request.getEntityId()).isEqualTo("99");
            var payload = request.getVessel();
            assertThat(payload.hasMmsi()).isFalse();
            assertThat(payload.hasVesselName()).isFalse();
            assertThat(payload.hasSpeedKnots()).isFalse();
            assertThat(payload.hasHeadingDeg()).isFalse();
            assertThat(payload.hasShipType()).isFalse();
            assertThat(payload.hasLocation()).isTrue();
        }
    }

    @Nested
    class BusMapping {

        private final BusVehicle.VehicleData v1 = new BusVehicle.VehicleData(11001, true, "ta", -23.54, -46.65);
        private final BusVehicle.VehicleData v2 = new BusVehicle.VehicleData(11002, false, "ta", -23.55, -46.66);

        @Test
        void returnsEmptyForNullSnapshot() {
            assertThat(mapper.toEvents((VehiclePosition.PositionResponse) null)).isEmpty();
        }

        @Test
        void mapsVehiclesOfEachLineWithItsLineSign() {
            var positions = new VehiclePosition.PositionResponse("12:00", List.of(
                    new VehiclePosition.LinePosition("8000-10", 1, 1, "A", "B", 1, List.of(v1)),
                    new VehiclePosition.LinePosition("8700-10", 2, 1, "C", "D", 1, List.of(v2)),
                    new VehiclePosition.LinePosition("9999-10", 3, 1, "E", "F", 0, null)
            ), List.of());

            var requests = mapper.toEvents(positions);

            assertThat(requests).hasSize(2);
            assertEnvelope(requests.getFirst(), "olhovivo", EntityType.BUS);
            assertThat(requests).extracting(r -> r.getBus().getLineCode()).containsExactly("8000-10", "8700-10");
            assertThat(requests).extracting(EventRequest::getEntityId).containsExactly("11001", "11002");
            assertThat(requests.getFirst().getBus().getVehicleId()).isEqualTo("11001");
            assertThat(requests.getFirst().getBus().getLocation().getLat()).isEqualTo(-23.54);
            assertThat(requests.getFirst().getBus().getLocation().getLon()).isEqualTo(-46.65);
        }

        @Test
        void mapsTopLevelVehiclesWithUnknownLineWhenNoLines() {
            var positions = new VehiclePosition.PositionResponse("12:00", null, List.of(v1, v2));

            var requests = mapper.toEvents(positions);

            assertThat(requests).hasSize(2)
                    .allSatisfy(r -> assertThat(r.getBus().getLineCode()).isEqualTo("unknown"));
        }

        @Test
        void returnsEmptyWhenSnapshotHasNoVehicles() {
            assertThat(mapper.toEvents(new VehiclePosition.PositionResponse("12:00", null, null))).isEmpty();
        }
    }

    @Nested
    class FloodingMapping {

        @Test
        void buildsNormalisedEntityIdAndSeverity() {
            var flood = new FloodingPoint.FloodData(LocalDate.of(2026, 11, 14), "Zona Leste", "São Mateus",
                    FloodingPoint.FloodStatus.ATIVO_INTRANSITAVEL, LocalTime.NOON, LocalTime.MIDNIGHT,
                    "AV SAPOPEMBA  NORTE", "CENTRO/BAIRRO", "REF");

            var request = mapper.toEvent(flood);

            assertEnvelope(request, "cge", EntityType.FLOOD_AREA);
            assertThat(request.getEntityId()).isEqualTo("Zona_Leste|São_Mateus|AV_SAPOPEMBA_NORTE");
            assertThat(request.getFloodArea().getSeverity()).isEqualTo("grave");
        }

        @Test
        void mapsTransitableStatusToModerate() {
            var flood = new FloodingPoint.FloodData(LocalDate.now(), "Z", "N",
                    FloodingPoint.FloodStatus.INATIVO_TRANSITAVEL, null, null, "S", null, null);

            assertThat(mapper.toEvent(flood).getFloodArea().getSeverity()).isEqualTo("moderado");
        }

        @Test
        void toleratesNullLocationParts() {
            var flood = new FloodingPoint.FloodData(LocalDate.now(), "Zona Sul", null,
                    FloodingPoint.FloodStatus.ATIVO_TRANSITAVEL, null, null, null, null, null);

            assertThat(mapper.toEvent(flood).getEntityId()).isEqualTo("Zona_Sul||");
        }
    }
}
