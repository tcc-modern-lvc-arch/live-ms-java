package io.github.raphonzius.lvc.live.quarkus.infrastructure;

import io.github.raphonzius.lvc.live.quarkus.domain.Vessel;
import io.github.raphonzius.lvc.live.quarkus.domain.VesselStreamingPort;
import io.github.raphonzius.lvc.proto.event.EntityType;
import io.github.raphonzius.lvc.proto.event.EventHubGrpc;
import io.github.raphonzius.lvc.proto.event.EventKind;
import io.github.raphonzius.lvc.proto.event.EventRequest;
import io.github.raphonzius.lvc.proto.event.Location;
import io.github.raphonzius.lvc.proto.event.LvcOrigin;
import io.github.raphonzius.lvc.proto.event.VesselPayload;
import io.quarkus.grpc.GrpcClient;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** Same EventRequest mapping as the Spring GrpcVesselStreamingAdapter (blocking stub). */
@Singleton
public class GrpcVesselStreamingAdapter implements VesselStreamingPort {

    private static final Logger LOG = Logger.getLogger(GrpcVesselStreamingAdapter.class);

    @GrpcClient("event-hub")
    EventHubGrpc.EventHubBlockingStub eventHubStub;

    @ConfigProperty(name = "event-hub.grpc.area-id", defaultValue = "default-area")
    String areaId;

    @Override
    public void publishVessel(Vessel vessel) {
        try {
            var request = buildEventRequest(vessel, Instant.now().toEpochMilli());
            eventHubStub.sendEvent(request);
            LOG.debugf("Published vessel to EventHub - id=%d, name=%s", vessel.id(), vessel.name());
        } catch (Exception e) {
            LOG.errorf(e, "Failed to publish vessel to EventHub - id=%d", vessel.id());
        }
    }

    @Override
    public void publishVesselBatch(Stream<? extends Vessel> vesselStream) {
        vesselStream.forEach(this::publishVessel);
    }

    EventRequest buildEventRequest(Vessel vessel, long timestampMs) {
        return switch (vessel) {
            case Vessel.VesselData data -> {
                var location = Location.newBuilder()
                        .setLat(data.latitude())
                        .setLon(data.longitude())
                        .build();
                var payload = buildPayload(data, location);
                var entityId = Objects.requireNonNullElse(data.mmsi(), data.id()).toString();

                yield EventRequest.newBuilder()
                        .setAreaId(areaId)
                        .setSource("ais")
                        .setEventKind(EventKind.MOVE)
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
        applyIfPresent(data.mmsi(), mmsi -> builder.setMmsi(mmsi.toString()));
        applyIfPresent(data.name(), builder::setVesselName);
        applyIfPresent(data.name_ais(), builder::setVesselName); // fallback (same as Spring)
        applyIfPresent(data.speed_over_ground(), sog -> builder.setSpeedKnots(sog.floatValue()));
        applyIfPresent(data.true_heading(), h -> builder.setHeadingDeg(h.floatValue()));
        applyIfPresent(data.shipType(), builder::setShipType);
        return builder.build();
    }

    private <T> void applyIfPresent(T value, Consumer<T> consumer) {
        if (value != null) {
            consumer.accept(value);
        }
    }
}
