package io.github.raphonzius.lvc.live.infrastructure.output.grpc;

import io.github.raphonzius.lvc.live.domain.flooding.FloodingPoint;
import io.github.raphonzius.lvc.live.domain.streaming.FloodingStreamingPort;
import io.github.raphonzius.lvc.proto.event.EntityType;
import io.github.raphonzius.lvc.proto.event.EventHubGrpc;
import io.github.raphonzius.lvc.proto.event.EventRequest;
import io.github.raphonzius.lvc.proto.event.FloodAreaPayload;
import io.github.raphonzius.lvc.proto.event.LvcOrigin;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import io.github.raphonzius.lvc.live.infrastructure.config.properties.EventHubProperties;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

/**
 * gRPC streaming adapter for CGESP flooding data.
 * Publishes flooding points to the EventHub via gRPC.
 *
 * Uses Java 21 patterns:
 * - Sealed interface exhaustive switch
 * - Record patterns with full component deconstruction
 * - Text blocks (where applicable)
 * - Type inference (var) throughout
 * - Functional composition with method references
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GrpcFloodingStreamingAdapter implements FloodingStreamingPort {

    private final EventHubGrpc.EventHubBlockingStub eventHubStub;
    private final EventHubProperties eventHubProperties;

    private final Function<String, String> normalize = s -> s.replaceAll("\\s+", "_");

    // Functional composition: entity ID builder using method reference chain
    private final Function<FloodingPoint.FloodData, String> entityIdBuilder = flooding ->
            String.join("|",
                    normalize.apply(flooding.zone()),
                    normalize.apply(flooding.neighborhood()),
                    normalize.apply(flooding.street()));

    @Override
    public void publishFloodings(LocalDate date, List<FloodingPoint.FloodData> floodings) {
        if (floodings == null || floodings.isEmpty()) {
            log.debug("No flooding points to publish for date={}", date);
            return;
        }

        var timestampMs = Instant.now().toEpochMilli();

        floodings.forEach(flooding -> publishSingleFlooding(flooding, timestampMs));

        log.debug("Published {} flooding point(s) to EventHub for date={}", floodings.size(), date);
    }

    private void publishSingleFlooding(FloodingPoint.FloodData flooding, long timestampMs) {
        try {
            var request = buildEventRequest(flooding, timestampMs);
            eventHubStub.sendEvent(request);
            log.debug("Published flooding point to EventHub — zone={}, neighborhood={}, status={}",
                    flooding.zone(), flooding.neighborhood(), flooding.status());
        } catch (Exception e) {
            log.error("Failed to publish flooding point to EventHub — zone={}, neighborhood={}",
                    flooding.zone(), flooding.neighborhood(), e);
        }
    }

    private EventRequest buildEventRequest(FloodingPoint flooding, long timestampMs) {
        // Full record pattern deconstruction with unnamed variables for unused components
        return switch (flooding) {
            case FloodingPoint.FloodData data -> {

                var floodPayload = FloodAreaPayload.newBuilder()
                        .setSeverity(mapSeverity(data.status()))
                        .build();

                yield EventRequest.newBuilder()
                        .setAreaId(eventHubProperties.grpc().areaId())
                        .setSource("cge")
                        .setEventKind(io.github.raphonzius.lvc.proto.event.EventKind.MOVE)
                        .setEntityType(EntityType.FLOOD_AREA)
                        .setLvc(LvcOrigin.LIVE)
                        .setTimestampMs(timestampMs)
                        .setEntityId(entityIdBuilder.apply(data))
                        .setFloodArea(floodPayload)
                        .build();
            }
        };
    }

    private String mapSeverity(FloodingPoint.FloodStatus status) {
        // Exhaustive switch on enum with multi-case labels
        return switch (status) {
            case ATIVO_INTRANSITAVEL, INATIVO_INTRANSITAVEL -> "grave";
            case ATIVO_TRANSITAVEL, INATIVO_TRANSITAVEL -> "moderado";
        };
    }
}
