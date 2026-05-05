package io.github.raphonzius.lvc.live.infrastructure.output.grpc;

import io.github.raphonzius.lvc.live.domain.bus.BusVehicle;
import io.github.raphonzius.lvc.live.domain.bus.VehiclePosition;
import io.github.raphonzius.lvc.live.domain.streaming.BusStreamingPort;
import io.github.raphonzius.lvc.proto.event.BusPayload;
import io.github.raphonzius.lvc.proto.event.EntityType;
import io.github.raphonzius.lvc.proto.event.EventHubGrpc;
import io.github.raphonzius.lvc.proto.event.EventRequest;
import io.github.raphonzius.lvc.proto.event.Location;
import io.github.raphonzius.lvc.proto.event.LvcOrigin;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import io.github.raphonzius.lvc.live.infrastructure.config.properties.EventHubProperties;

import java.time.Instant;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Stream;

/**
 * gRPC streaming adapter for bus position data.
 * Publishes SPTrans OlhoVivo vehicle positions to the EventHub via gRPC.
 *
 * Uses Java 21 patterns:
 * - Record patterns with exhaustive switch
 * - Pattern matching for instanceof elimination
 * - Unnamed variables (_)
 * - Streamlined Optional chains
 * - Type inference (var) for local variables
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GrpcBusStreamingAdapter implements BusStreamingPort {

    private final EventHubGrpc.EventHubBlockingStub eventHubStub;
    private final EventHubProperties eventHubProperties;

    private final BiFunction<BusVehicle.VehicleData, String, EventRequest> requestBuilder = (vehicle, lineCode) -> {
        var timestampMs = Instant.now().toEpochMilli();
        var busPayload = BusPayload.newBuilder()
                .setLocation(Location.newBuilder()
                        .setLat(vehicle.py())
                        .setLon(vehicle.px())
                        .build())
                .setLineCode(lineCode)
                .setVehicleId(String.valueOf(vehicle.p()))
                .build();

        return EventRequest.newBuilder()
                .setAreaId(eventHubProperties.grpc().areaId())
                .setSource("olhovivo")
                .setEventKind(io.github.raphonzius.lvc.proto.event.EventKind.MOVE)
                .setEntityType(EntityType.BUS)
                .setLvc(LvcOrigin.LIVE)
                .setTimestampMs(timestampMs)
                .setEntityId(String.valueOf(vehicle.p()))
                .setBus(busPayload)
                .build();
    };

    @Override
    public void publishPositions(VehiclePosition.PositionResponse positions) {
        if (positions == null) {
            return;
        }

        var timestampMs = Instant.now().toEpochMilli();
        var vehicles = extractVehicles(positions);

        vehicles.forEach(vehicle -> publishSingleVehicle(vehicle, positions, timestampMs));
    }

    private void publishSingleVehicle(BusVehicle.VehicleData vehicle,
                                      VehiclePosition.PositionResponse positions,
                                      long timestampMs) {
        try {
            var lineCode = findLineCode(positions, vehicle);
            var request = requestBuilder.apply(vehicle, lineCode);
            eventHubStub.sendEvent(request);
            log.debug("Published bus position to EventHub — vehicle={}, line={}", vehicle.p(), lineCode);
        } catch (Exception e) {
            log.error("Failed to publish bus position to EventHub — vehicle={}", vehicle.p(), e);
        }
    }

    private List<BusVehicle.VehicleData> extractVehicles(VehiclePosition.PositionResponse positions) {
        // Exhaustive switch with record patterns and null guards
        return switch (positions) {
            case VehiclePosition.PositionResponse(_, var lines, var vs)
                    when lines != null && !lines.isEmpty() ->
                    lines.stream()
                            .flatMap(l -> l.vs() != null ? l.vs().stream() : Stream.of())
                            .toList();
            case VehiclePosition.PositionResponse(_, _, var vs)
                    when vs != null -> vs;
            default -> List.of();
        };
    }

    private String findLineCode(VehiclePosition.PositionResponse positions, BusVehicle.VehicleData vehicle) {
        return switch (positions) {
            case VehiclePosition.PositionResponse(_, var lines, _)
                    when lines != null ->
                    lines.stream()
                            .filter(l -> l.vs() != null && l.vs().contains(vehicle))
                            .findFirst()
                            .map(VehiclePosition.LinePosition::c)
                            .orElse("unknown");
            default -> "unknown";
        };
    }
}
