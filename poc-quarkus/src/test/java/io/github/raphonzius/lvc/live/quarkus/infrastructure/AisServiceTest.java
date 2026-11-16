package io.github.raphonzius.lvc.live.quarkus.infrastructure;

import io.github.raphonzius.lvc.live.quarkus.application.AisService;
import io.github.raphonzius.lvc.live.quarkus.domain.Vessel;
import io.github.raphonzius.lvc.proto.event.EventAck;
import io.github.raphonzius.lvc.proto.event.EventHubGrpc;
import io.github.raphonzius.lvc.proto.event.EventRequest;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.stream.IntStream;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class AisServiceTest {

    @Inject
    AisService service;

    @InjectMock
    @RestClient
    AisClient aisClient;

    // quarkus-grpc exposes the stub as a final-class @Singleton, which @InjectMock cannot replace;
    // the adapter is @Singleton (no client proxy) so the test swaps its stub field with a Mockito mock.
    @Inject
    GrpcVesselStreamingAdapter adapter;

    EventHubGrpc.EventHubBlockingStub stub;

    @BeforeEach
    void setUp() {
        stub = Mockito.mock(EventHubGrpc.EventHubBlockingStub.class);
        adapter.eventHubStub = stub;
    }

    static Vessel.VesselData vessel(long id) {
        return new Vessel.VesselData(id, null, null, null, 1000L + id, "V" + id, null, null, 7L, 1L,
                null, null, null, null, null, null, 90L, null, 12.5, null, null, null, -23.0, -45.0, null, null);
    }

    @Test
    void nVesselsProduceNStubCalls() {
        when(aisClient.getVessels(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(IntStream.range(0, 5).mapToObj(AisServiceTest::vessel).toList());
        when(stub.sendEvent(any(EventRequest.class))).thenReturn(EventAck.getDefaultInstance());

        assertEquals(5, service.fetchAndStreamVessels());

        var captor = ArgumentCaptor.forClass(EventRequest.class);
        verify(stub, times(5)).sendEvent(captor.capture());
        var first = captor.getAllValues().getFirst();
        assertEquals("ais", first.getSource());
        assertEquals("1000", first.getEntityId());
        assertEquals("V0", first.getVessel().getVesselName());
    }

    @Test
    void pollEndpointTriggersCycle() {
        when(aisClient.getVessels(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(vessel(1), vessel(2)));
        given().when().post("/api/v1/vessels/poll").then().statusCode(200);
        given().when().get("/api/v1/vessels/health").then().statusCode(200);
    }
}
