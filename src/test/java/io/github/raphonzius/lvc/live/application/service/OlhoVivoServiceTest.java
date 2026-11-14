package io.github.raphonzius.lvc.live.application.service;

import io.github.raphonzius.lvc.live.domain.FetchResult;
import io.github.raphonzius.lvc.live.domain.bus.BusLine;
import io.github.raphonzius.lvc.live.domain.bus.BusVehicle;
import io.github.raphonzius.lvc.live.domain.bus.OlhoVivoPort;
import io.github.raphonzius.lvc.live.domain.bus.VehiclePosition;
import io.github.raphonzius.lvc.live.domain.streaming.BusStreamingPort;
import io.github.raphonzius.lvc.live.domain.streaming.PublishResult;
import io.github.raphonzius.lvc.live.infrastructure.config.properties.OlhoVivoProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OlhoVivoService} orchestration.
 */
@ExtendWith(MockitoExtension.class)
class OlhoVivoServiceTest {

    @Mock
    private OlhoVivoPort olhoVivoPort;

    @Mock
    private BusStreamingPort busStreamingPort;

    @Mock
    private OlhoVivoProperties properties;

    @InjectMocks
    private OlhoVivoService service;

    @org.junit.jupiter.api.BeforeEach
    void publishingSucceedsByDefault() {
        org.mockito.Mockito.lenient().when(busStreamingPort.publishPositions(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PublishResult(1, 0));
    }

    // -------------------------------------------------------------------------
    // fetchAndStreamVehiclePositions — empty config
    // -------------------------------------------------------------------------

    @Test
    void fetchAndStreamVehiclePositions_emptyLineTerms_skipsWithNoInteractions() {
        when(properties.polling()).thenReturn(new OlhoVivoProperties.Polling(60000, Map.of()));

        service.fetchAndStreamVehiclePositions();

        verifyNoInteractions(olhoVivoPort);
        verifyNoInteractions(busStreamingPort);
    }

    // -------------------------------------------------------------------------
    // fetchAndStreamVehiclePositions — tl filtering
    // -------------------------------------------------------------------------

    @Test
    void fetchAndStreamVehiclePositions_publishesMatchingTlLines() {
        when(properties.polling()).thenReturn(new OlhoVivoProperties.Polling(60000, Map.of("178L", List.of(10))));
        List<BusLine.LineData> lines = List.of(lineData(1001, 10), lineData(1002, 21));
        when(olhoVivoPort.searchLines("178L")).thenReturn(FetchResult.of(lines));

        VehiclePosition.PositionResponse pos = positionResponse(1001, List.of(vehicleData(42)));
        when(olhoVivoPort.positionsByLine(1001)).thenReturn(FetchResult.of(pos));

        service.fetchAndStreamVehiclePositions();

        verify(olhoVivoPort).positionsByLine(1001);
        verify(olhoVivoPort, never()).positionsByLine(1002);
        verify(busStreamingPort).publishPositions(pos);
    }

    @Test
    void fetchAndStreamVehiclePositions_filtersOutNonMatchingTl() {
        when(properties.polling()).thenReturn(new OlhoVivoProperties.Polling(60000, Map.of("5000", List.of(10))));
        List<BusLine.LineData> lines = List.of(lineData(2001, 21), lineData(2002, 32));
        when(olhoVivoPort.searchLines("5000")).thenReturn(FetchResult.of(lines));

        service.fetchAndStreamVehiclePositions();

        verifyNoInteractions(busStreamingPort);
    }

    @Test
    void fetchAndStreamVehiclePositions_multipleTlValuesAllMatch() {
        when(properties.polling()).thenReturn(
                new OlhoVivoProperties.Polling(60000, Map.of("8000", List.of(10, 21))));
        List<BusLine.LineData> lines = List.of(lineData(3001, 10), lineData(3002, 21), lineData(3003, 32));
        when(olhoVivoPort.searchLines("8000")).thenReturn(FetchResult.of(lines));

        VehiclePosition.PositionResponse pos1 = positionResponse(3001, List.of(vehicleData(1)));
        VehiclePosition.PositionResponse pos2 = positionResponse(3002, List.of(vehicleData(2)));
        when(olhoVivoPort.positionsByLine(3001)).thenReturn(FetchResult.of(pos1));
        when(olhoVivoPort.positionsByLine(3002)).thenReturn(FetchResult.of(pos2));

        service.fetchAndStreamVehiclePositions();

        verify(busStreamingPort).publishPositions(pos1);
        verify(busStreamingPort).publishPositions(pos2);
        verify(olhoVivoPort, never()).positionsByLine(3003);
    }

    // -------------------------------------------------------------------------
    // fetchAndStreamVehiclePositions — deduplication
    // -------------------------------------------------------------------------

    @Test
    void fetchAndStreamVehiclePositions_deduplicatesClAcrossTerms() {
        // two terms both resolve to cl=1001 with tl=10
        when(properties.polling()).thenReturn(new OlhoVivoProperties.Polling(60000,
                Map.of("termA", List.of(10), "termB", List.of(10))));

        when(olhoVivoPort.searchLines("termA")).thenReturn(FetchResult.of(List.of(lineData(1001, 10))));
        when(olhoVivoPort.searchLines("termB")).thenReturn(FetchResult.of(List.of(lineData(1001, 10))));

        VehiclePosition.PositionResponse pos = positionResponse(1001, List.of(vehicleData(99)));
        when(olhoVivoPort.positionsByLine(1001)).thenReturn(FetchResult.of(pos));

        service.fetchAndStreamVehiclePositions();

        // cl=1001 fetched only once despite appearing twice
        verify(olhoVivoPort).positionsByLine(1001);
        verify(busStreamingPort).publishPositions(pos);
    }

    // -------------------------------------------------------------------------
    // fetchAndStreamVehiclePositions — skip empty/null responses
    // -------------------------------------------------------------------------

    @Test
    void fetchAndStreamVehiclePositions_skipsEmptyPositionResponse() {
        when(properties.polling()).thenReturn(new OlhoVivoProperties.Polling(60000, Map.of("178L", List.of(10))));
        when(olhoVivoPort.searchLines("178L")).thenReturn(FetchResult.of(List.of(lineData(1001, 10))));
        when(olhoVivoPort.positionsByLine(1001)).thenReturn(FetchResult.of(VehiclePosition.PositionResponse.empty()));

        service.fetchAndStreamVehiclePositions();

        verifyNoInteractions(busStreamingPort);
    }

    @Test
    void fetchAndStreamVehiclePositions_skipsNullPositionResponse() {
        when(properties.polling()).thenReturn(new OlhoVivoProperties.Polling(60000, Map.of("178L", List.of(10))));
        when(olhoVivoPort.searchLines("178L")).thenReturn(FetchResult.of(List.of(lineData(1001, 10))));
        when(olhoVivoPort.positionsByLine(1001)).thenReturn(FetchResult.of(null));

        service.fetchAndStreamVehiclePositions();

        verifyNoInteractions(busStreamingPort);
    }

    // -------------------------------------------------------------------------
    // Result reporting
    // -------------------------------------------------------------------------

    @Test
    void fetchAndStreamVehiclePositions_sumsPublishResultsAcrossLines() {
        when(properties.polling()).thenReturn(new OlhoVivoProperties.Polling(60000, Map.of("8000", List.of(10))));
        when(olhoVivoPort.searchLines("8000"))
                .thenReturn(FetchResult.of(List.of(lineData(3001, 10), lineData(3002, 10))));
        var pos1 = positionResponse(3001, List.of(vehicleData(1)));
        var pos2 = positionResponse(3002, List.of(vehicleData(2)));
        when(olhoVivoPort.positionsByLine(3001)).thenReturn(FetchResult.of(pos1));
        when(olhoVivoPort.positionsByLine(3002)).thenReturn(FetchResult.of(pos2));
        when(busStreamingPort.publishPositions(pos1)).thenReturn(new PublishResult(1, 0));
        when(busStreamingPort.publishPositions(pos2)).thenReturn(new PublishResult(0, 1));

        assertThat(service.fetchAndStreamVehiclePositions()).isEqualTo(new PublishResult(1, 1));
    }

    @Test
    void fetchAndStreamVehiclePositions_everySearchUnavailableIsATotalFailure() {
        when(properties.polling()).thenReturn(
                new OlhoVivoProperties.Polling(60000, Map.of("a", List.of(10), "b", List.of(10))));
        when(olhoVivoPort.searchLines("a")).thenReturn(FetchResult.unavailable("circuit_open"));
        when(olhoVivoPort.searchLines("b")).thenReturn(FetchResult.unavailable("circuit_open"));

        var result = service.fetchAndStreamVehiclePositions();

        assertThat(result).isEqualTo(new PublishResult(0, 2));
        assertThat(result.totalFailure()).isTrue();
        verifyNoInteractions(busStreamingPort);
    }

    @Test
    void fetchAndStreamVehiclePositions_unavailablePositionsMakeTheCyclePartial() {
        when(properties.polling()).thenReturn(new OlhoVivoProperties.Polling(60000, Map.of("8000", List.of(10))));
        when(olhoVivoPort.searchLines("8000"))
                .thenReturn(FetchResult.of(List.of(lineData(3001, 10), lineData(3002, 10))));
        var pos1 = positionResponse(3001, List.of(vehicleData(1)));
        when(olhoVivoPort.positionsByLine(3001)).thenReturn(FetchResult.of(pos1));
        when(olhoVivoPort.positionsByLine(3002)).thenReturn(FetchResult.unavailable("timeout"));
        when(busStreamingPort.publishPositions(pos1)).thenReturn(new PublishResult(4, 0));

        var result = service.fetchAndStreamVehiclePositions();

        assertThat(result).isEqualTo(new PublishResult(4, 1));
        assertThat(result.totalFailure()).isFalse();
    }

    @Test
    void searchLinesAndPositions_unavailableStillAnswerEmptyForTheRestApi() {
        when(olhoVivoPort.searchLines("x")).thenReturn(FetchResult.unavailable("down"));
        when(olhoVivoPort.positionsByLine(1)).thenReturn(FetchResult.unavailable("down"));

        assertThat(service.searchLines("x")).isEmpty();
        assertThat(service.positionsByLine(1)).isEqualTo(VehiclePosition.PositionResponse.empty());
    }

    // -------------------------------------------------------------------------
    // searchLines / positionsByLine — delegation
    // -------------------------------------------------------------------------

    @Test
    void searchLines_delegatesToPort() {
        List<BusLine.LineData> expected = List.of(lineData(1001, 10));
        when(olhoVivoPort.searchLines("178L")).thenReturn(FetchResult.of(expected));

        List<BusLine.LineData> result = service.searchLines("178L");

        assertThat(result).isSameAs(expected);
        verify(olhoVivoPort).searchLines("178L");
        verifyNoInteractions(busStreamingPort);
    }

    @Test
    void positionsByLine_delegatesToPort() {
        VehiclePosition.PositionResponse expected = positionResponse(1001, List.of(vehicleData(5)));
        when(olhoVivoPort.positionsByLine(1001)).thenReturn(FetchResult.of(expected));

        VehiclePosition.PositionResponse result = service.positionsByLine(1001);

        assertThat(result).isSameAs(expected);
        verify(olhoVivoPort).positionsByLine(1001);
        verifyNoInteractions(busStreamingPort);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static BusLine.LineData lineData(int cl, int tl) {
        return new BusLine.LineData(cl, false, "178L", 1, tl, "TERM PRINCIPAL", "TERM SECUNDARIO");
    }

    private static BusVehicle.VehicleData vehicleData(int prefix) {
        return new BusVehicle.VehicleData(prefix, true, "2026-04-06T18:00:00Z", -23.5, -46.5);
    }

    private static VehiclePosition.PositionResponse positionResponse(
            int cl, List<BusVehicle.VehicleData> vehicles) {
        return new VehiclePosition.PositionResponse("18:00", List.of(), vehicles);
    }
}
