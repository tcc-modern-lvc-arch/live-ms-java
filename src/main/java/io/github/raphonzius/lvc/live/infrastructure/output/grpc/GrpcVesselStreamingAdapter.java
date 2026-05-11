package io.github.raphonzius.lvc.live.infrastructure.output.grpc;

import io.github.raphonzius.lvc.live.domain.streaming.VesselStreamingPort;
import io.github.raphonzius.lvc.live.domain.vessel.Vessel;
import io.github.raphonzius.lvc.proto.event.EntityType;
import io.github.raphonzius.lvc.proto.event.EventHubGrpc;
import io.github.raphonzius.lvc.proto.event.EventRequest;
import io.github.raphonzius.lvc.proto.event.Location;
import io.github.raphonzius.lvc.proto.event.LvcOrigin;
import io.github.raphonzius.lvc.proto.event.VesselPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import io.github.raphonzius.lvc.live.infrastructure.config.properties.EventHubProperties;

import java.time.Instant;
import java.util.Objects;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class GrpcVesselStreamingAdapter implements VesselStreamingPort {

    private final EventHubGrpc.EventHubBlockingStub eventHubStub;
    private final EventHubProperties eventHubProperties;

    @Override
    public void publishVessel(Vessel vessel) {
        try {
            var timestampMs = Instant.now().toEpochMilli();
            var request = buildEventRequest(vessel, timestampMs);
            eventHubStub.sendEvent(request);
            log.debug("Published vessel to EventHub — id={}, name={}", vessel.id(), vessel.name());
        } catch (Exception e) {
            log.error("Failed to publish vessel to EventHub — id={}", vessel.id(), e);
        }
    }

    @Override
    public void publishVesselBatch(Stream<? extends Vessel> vesselStream) {
        vesselStream.forEach(this::publishVessel);
    }

    private EventRequest buildEventRequest(Vessel vessel, long timestampMs) {
        // Exhaustive switch with type pattern matching
        return switch (vessel) {
            case Vessel.VesselData data -> {

                var location = Location.newBuilder()
                        .setLat(data.latitude())
                        .setLon(data.longitude())
                        .build();
                var payload = buildPayload(data, location);
                var entityId = Objects.requireNonNullElse(data.mmsi(), data.id()).toString();

                yield EventRequest.newBuilder()
                        .setAreaId(eventHubProperties.grpc().areaId())
                        .setSource("ais")
                        .setEventKind(io.github.raphonzius.lvc.proto.event.EventKind.MOVE)
                        .setEntityType(EntityType.VESSEL)
                        .setLvc(LvcOrigin.LIVE)
                        .setTimestampMs(timestampMs)
                        .setEntityId(entityId)
                        .setVessel(payload)
                        .build();
            }
        };
    }

    private VesselPayload buildPayload(Vessel.VesselData data, Location location) {
        var builder = VesselPayload.newBuilder().setLocation(location);

        // Pattern: applyIfPresent using Objects.nonNull and method references
        applyIfPresent(data.mmsi(), mmsi -> builder.setMmsi(mmsi.toString()));
        applyIfPresent(data.name(), builder::setVesselName);
        applyIfPresent(data.name_ais(), builder::setVesselName); // fallback
        applyIfPresent(data.speed_over_ground(), sog -> builder.setSpeedKnots(sog.floatValue()));
        applyIfPresent(data.true_heading(), h -> builder.setHeadingDeg(h.floatValue()));
        applyIfPresent(data.shipType(), builder::setShipType);

        return builder.build();
    }

    private <T> void applyIfPresent(T value, java.util.function.Consumer<T> consumer) {
        if (value != null) {
            consumer.accept(value);
        }
    }
}
