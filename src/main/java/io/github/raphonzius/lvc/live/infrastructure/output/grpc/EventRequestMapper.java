package io.github.raphonzius.lvc.live.infrastructure.output.grpc;

import io.github.raphonzius.lvc.live.domain.bus.BusVehicle;
import io.github.raphonzius.lvc.live.domain.bus.VehiclePosition;
import io.github.raphonzius.lvc.live.domain.flooding.FloodingPoint;
import io.github.raphonzius.lvc.live.domain.vessel.Vessel;
import io.github.raphonzius.lvc.proto.event.BusPayload;
import io.github.raphonzius.lvc.proto.event.EntityType;
import io.github.raphonzius.lvc.proto.event.EventKind;
import io.github.raphonzius.lvc.proto.event.EventRequest;
import io.github.raphonzius.lvc.proto.event.FloodAreaPayload;
import io.github.raphonzius.lvc.proto.event.Location;
import io.github.raphonzius.lvc.proto.event.LvcOrigin;
import io.github.raphonzius.lvc.proto.event.VesselPayload;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Anti-corruption layer from the domain model to the event-hub protobuf contract
 * ({@code event.proto}). Pure and stateless apart from the area id and clock, so it is
 * unit-testable without gRPC or Spring; the streaming adapters only deal with transport.
 */
public final class EventRequestMapper {

    static final String UNKNOWN_LINE = "unknown";

    private final String areaId;
    private final Clock clock;

    public EventRequestMapper(String areaId, Clock clock) {
        this.areaId = Objects.requireNonNull(areaId, "areaId");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    // ── Vessel (AIS) ─────────────────────────────────────────────────────────

    public EventRequest toEvent(Vessel vessel) {
        // Exhaustive over the sealed hierarchy (single permitted record) — no default branch.
        return switch (vessel) {
            case Vessel.VesselData data -> {
                var payload = VesselPayload.newBuilder().setLocation(location(data.latitude(), data.longitude()));
                ifPresent(data.mmsi(), mmsi -> payload.setMmsi(mmsi.toString()));
                ifPresent(data.name(), payload::setVesselName);
                ifPresent(data.speedKnots(), sog -> payload.setSpeedKnots(sog.floatValue()));
                ifPresent(data.headingDeg(), heading -> payload.setHeadingDeg(heading.floatValue()));
                ifPresent(data.shipType(), payload::setShipType);

                var entityId = data.mmsi() != null ? data.mmsi().toString() : Long.toString(data.id());
                yield envelope("ais", EntityType.VESSEL, entityId).setVessel(payload).build();
            }
        };
    }

    // ── Bus (OlhoVivo) ───────────────────────────────────────────────────────

    /**
     * Maps a position snapshot to one event per vehicle. When the snapshot carries per-line
     * groups ({@code /Posicao}) each vehicle is tagged with its line sign; a single-line
     * snapshot ({@code /Posicao/Linha}) has no sign in the payload, so {@value #UNKNOWN_LINE} is used.
     */
    public List<EventRequest> toEvents(VehiclePosition.PositionResponse positions) {
        if (positions == null) {
            return List.of();
        }
        if (positions.l() != null && !positions.l().isEmpty()) {
            return positions.l().stream()
                    .flatMap(line -> nullToEmpty(line.vs()).map(vehicle -> toEvent(vehicle, line.c())))
                    .toList();
        }
        return nullToEmpty(positions.vs()).map(vehicle -> toEvent(vehicle, UNKNOWN_LINE)).toList();
    }

    private EventRequest toEvent(BusVehicle.VehicleData vehicle, String lineCode) {
        var vehicleId = Integer.toString(vehicle.p());
        var payload = BusPayload.newBuilder()
                .setLocation(location(vehicle.py(), vehicle.px()))
                .setLineCode(lineCode)
                .setVehicleId(vehicleId);
        return envelope("olhovivo", EntityType.BUS, vehicleId).setBus(payload).build();
    }

    // ── Flooding (CGESP) ─────────────────────────────────────────────────────

    public EventRequest toEvent(FloodingPoint.FloodData flooding) {
        var entityId = String.join("|",
                normalize(flooding.zone()), normalize(flooding.neighborhood()), normalize(flooding.street()));
        var payload = FloodAreaPayload.newBuilder().setSeverity(severity(flooding.status()));
        return envelope("cge", EntityType.FLOOD_AREA, entityId).setFloodArea(payload).build();
    }

    private static String severity(FloodingPoint.FloodStatus status) {
        return switch (status) {
            case ATIVO_INTRANSITAVEL, INATIVO_INTRANSITAVEL -> "grave";
            case ATIVO_TRANSITAVEL, INATIVO_TRANSITAVEL -> "moderado";
        };
    }

    // ── Shared helpers ───────────────────────────────────────────────────────

    private EventRequest.Builder envelope(String source, EntityType type, String entityId) {
        return EventRequest.newBuilder()
                .setAreaId(areaId)
                .setSource(source)
                .setEventKind(EventKind.MOVE)
                .setEntityType(type)
                .setLvc(LvcOrigin.LIVE)
                .setTimestampMs(clock.millis())
                .setEntityId(entityId);
    }

    private static Location location(double lat, double lon) {
        return Location.newBuilder().setLat(lat).setLon(lon).build();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "_");
    }

    private static <T> Stream<T> nullToEmpty(List<T> list) {
        return list == null ? Stream.empty() : list.stream();
    }

    private static <T> void ifPresent(T value, Consumer<T> setter) {
        if (value != null) {
            setter.accept(value);
        }
    }
}
