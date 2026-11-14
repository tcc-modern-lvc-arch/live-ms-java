package io.github.raphonzius.lvc.live.infrastructure.output.grpc;

import io.github.raphonzius.lvc.live.domain.bus.BusVehicle;
import io.github.raphonzius.lvc.live.domain.bus.VehiclePosition;
import io.github.raphonzius.lvc.live.domain.flooding.FloodingPoint;
import io.github.raphonzius.lvc.live.domain.streaming.PublishResult;
import io.github.raphonzius.lvc.live.domain.vessel.Vessel;
import io.github.raphonzius.lvc.proto.event.EventAck;
import io.grpc.Status;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The streaming adapters return a domain {@link PublishResult} (publisher failures are no longer
 * dropped) and isolate mapping failures per item.
 */
class GrpcStreamingAdaptersTest {

    private static final EventAck ACCEPTED = EventAck.newBuilder().setStatus("accepted").build();

    private final EventRequestMapper mapper = new EventRequestMapper("area", Clock.systemUTC());
    private final List<String> sentIds = new CopyOnWriteArrayList<>();

    private EventPublisher okPublisher() {
        return new EventPublisher(r -> {
            sentIds.add(r.getEntityId());
            return ACCEPTED;
        }, 4, 10);
    }

    private static EventPublisher hubDownPublisher() {
        return new EventPublisher(_ -> {
            throw Status.UNAVAILABLE.withDescription("hub down").asRuntimeException();
        }, 4, 10);
    }

    private static Vessel.VesselData vessel(long id) {
        return new Vessel.VesselData(id, null, "V" + id, "Type_1", -23.0, -46.0, 0L, null, null);
    }

    private static FloodingPoint.FloodData flood(String street, FloodingPoint.FloodStatus status) {
        return new FloodingPoint.FloodData(LocalDate.of(2026, 1, 1), "Z", "N", status,
                LocalTime.NOON, LocalTime.MIDNIGHT, street, "d", "r");
    }

    private static VehiclePosition.PositionResponse positions(int vehicles) {
        var vs = new ArrayList<BusVehicle.VehicleData>();
        for (int i = 0; i < vehicles; i++) {
            vs.add(new BusVehicle.VehicleData(i, true, "t", -23.0, -46.0));
        }
        return new VehiclePosition.PositionResponse("12:00", List.of(), vs);
    }

    // ── result propagation ──────────────────────────────────────────────────

    @Test
    void vesselBatchResultReflectsDeliveries() {
        var adapter = new GrpcVesselStreamingAdapter(mapper, okPublisher());

        assertThat(adapter.publishVesselBatch(Stream.of(vessel(1), vessel(2)))).isEqualTo(new PublishResult(2, 0));
    }

    @Test
    void vesselBatchResultReportsTotalFailureWhenHubIsDown() {
        var adapter = new GrpcVesselStreamingAdapter(mapper, hubDownPublisher());

        var result = adapter.publishVesselBatch(Stream.of(vessel(1), vessel(2)));

        assertThat(result).isEqualTo(new PublishResult(0, 2));
        assertThat(result.totalFailure()).isTrue();
    }

    @Test
    void singleVesselResultIsPropagated() {
        assertThat(new GrpcVesselStreamingAdapter(mapper, hubDownPublisher()).publishVessel(vessel(1)))
                .isEqualTo(new PublishResult(0, 1));
    }

    @Test
    void busResultIsPropagated() {
        assertThat(new GrpcBusStreamingAdapter(mapper, okPublisher()).publishPositions(positions(3)))
                .isEqualTo(new PublishResult(3, 0));
        assertThat(new GrpcBusStreamingAdapter(mapper, hubDownPublisher()).publishPositions(positions(3)))
                .isEqualTo(new PublishResult(0, 3));
    }

    @Test
    void floodingResultIsPropagatedAndEmptyIsEmpty() {
        var date = LocalDate.of(2026, 1, 1);
        var points = List.of(flood("A", FloodingPoint.FloodStatus.ATIVO_TRANSITAVEL));

        assertThat(new GrpcFloodingStreamingAdapter(mapper, okPublisher()).publishFloodings(date, points))
                .isEqualTo(new PublishResult(1, 0));
        assertThat(new GrpcFloodingStreamingAdapter(mapper, hubDownPublisher()).publishFloodings(date, points))
                .isEqualTo(new PublishResult(0, 1));
        assertThat(new GrpcFloodingStreamingAdapter(mapper, okPublisher()).publishFloodings(date, List.of()))
                .isEqualTo(PublishResult.EMPTY);
    }

    // ── per-item mapping isolation ──────────────────────────────────────────

    @Test
    void vesselThatFailsToMapIsCountedAsFailedAndTheBatchContinues() {
        var adapter = new GrpcVesselStreamingAdapter(mapper, okPublisher());

        var result = adapter.publishVesselBatch(Arrays.asList(vessel(1), null, vessel(3)).stream());

        assertThat(result).isEqualTo(new PublishResult(2, 1));
        assertThat(sentIds).containsExactlyInAnyOrder("1", "3");
    }

    @Test
    void floodingThatFailsToMapIsCountedAsFailedAndTheBatchContinues() {
        var adapter = new GrpcFloodingStreamingAdapter(mapper, okPublisher());
        var points = Arrays.asList(
                flood("A", FloodingPoint.FloodStatus.ATIVO_INTRANSITAVEL),
                flood("B", null), // severity(null) throws in the mapper
                flood("C", FloodingPoint.FloodStatus.INATIVO_TRANSITAVEL));

        var result = adapter.publishFloodings(LocalDate.of(2026, 1, 1), points);

        assertThat(result).isEqualTo(new PublishResult(2, 1));
        assertThat(sentIds).hasSize(2);
    }

    @Test
    void emptyVesselBatchIsEmptyResult() {
        assertThat(new GrpcVesselStreamingAdapter(mapper, okPublisher()).publishVesselBatch(Stream.empty()))
                .isEqualTo(PublishResult.EMPTY);
        assertThat(Collections.<String>emptyList()).isEmpty();
    }
}
