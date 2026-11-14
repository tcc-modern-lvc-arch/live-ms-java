package io.github.raphonzius.lvc.live.infrastructure.output.grpc;

import io.github.raphonzius.lvc.proto.event.EventAck;
import io.github.raphonzius.lvc.proto.event.EventHubGrpc;
import io.github.raphonzius.lvc.proto.event.EventRequest;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.InsecureServerCredentials;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * {@link GrpcEventSender} applies a per-call deadline to every unary SendEvent RPC.
 */
class GrpcEventSenderTest {

    private Server server;
    private ManagedChannel channel;

    private EventHubGrpc.EventHubBlockingStub stubFor(EventHubGrpc.EventHubImplBase service) throws IOException {
        server = Grpc.newServerBuilderForPort(0, InsecureServerCredentials.create())
                .addService(service).build().start();
        channel = Grpc.newChannelBuilder("localhost:" + server.getPort(), InsecureChannelCredentials.create()).build();
        return EventHubGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() {
        if (channel != null) channel.shutdownNow();
        if (server != null) server.shutdownNow();
    }

    @Test
    void returnsAckFromHub() throws IOException {
        var sender = new GrpcEventSender(stubFor(new EventHubGrpc.EventHubImplBase() {
            @Override
            public void sendEvent(EventRequest request, StreamObserver<EventAck> observer) {
                observer.onNext(EventAck.newBuilder().setEventId(request.getEntityId()).setStatus("accepted").build());
                observer.onCompleted();
            }
        }), Duration.ofSeconds(2));

        var ack = sender.send(EventRequest.newBuilder().setEntityId("v-1").build());

        assertThat(ack.getEventId()).isEqualTo("v-1");
    }

    @Test
    void failsWithDeadlineExceededWhenHubIsSlowerThanDeadline() throws IOException {
        var stub = stubFor(new EventHubGrpc.EventHubImplBase() {
            @Override
            public void sendEvent(EventRequest request, StreamObserver<EventAck> observer) {
                // never answers — the client deadline must fire
            }
        });
        var sender = new GrpcEventSender(stub, Duration.ofMillis(50));

        assertThatExceptionOfType(StatusRuntimeException.class)
                .isThrownBy(() -> sender.send(EventRequest.getDefaultInstance()))
                .satisfies(e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.DEADLINE_EXCEEDED));
    }
}
