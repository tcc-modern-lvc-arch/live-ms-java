package io.github.raphonzius.lvc.live.application.service;

import io.github.raphonzius.lvc.live.domain.FetchResult;
import io.github.raphonzius.lvc.live.domain.bus.BusLine;
import io.github.raphonzius.lvc.live.domain.bus.OlhoVivoPort;
import io.github.raphonzius.lvc.live.domain.bus.VehiclePosition;
import io.github.raphonzius.lvc.live.domain.streaming.BusStreamingPort;
import io.github.raphonzius.lvc.live.domain.streaming.PublishResult;
import io.github.raphonzius.lvc.live.infrastructure.config.properties.OlhoVivoProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Application service for OlhoVivo SPTrans bus data orchestration.
 *
 * Targeted polling workflow:
 *   1. For each configured lineTerms → /Linha/Buscar → resolve cl codes
 *   2. For each cl → /Posicao/Linha → vehicle positions
 *   3. Publish each PositionResponse to Event Hub via gRPC
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OlhoVivoService {

    private final OlhoVivoPort olhoVivoPort;
    private final BusStreamingPort busStreamingPort;
    private final OlhoVivoProperties properties;

    /**
     * Executes the targeted polling workflow:
     * searches configured line terms → filters by {@code tl} → fetches positions per line → publishes to Event Hub.
     *
     * @return delivered/failed counters; every unavailable upstream call (search or positions) counts as
     *         one failed unit, so a cycle where OlhoVivo is down is reported as failed, not as empty
     */
    public PublishResult fetchAndStreamVehiclePositions() {
        Map<String, List<Integer>> lineTerms = properties.polling().lineTerms();

        if (lineTerms.isEmpty()) {
            log.warn("No olhovivo.polling.line-terms configured — skipping poll");
            return PublishResult.EMPTY;
        }

        var unavailable = 0;
        var lineCodes = new LinkedHashSet<Integer>();
        for (var entry : lineTerms.entrySet()) {
            switch (olhoVivoPort.searchLines(entry.getKey())) {
                case FetchResult.Unavailable<List<BusLine.LineData>> _ -> unavailable++;
                case FetchResult.Fetched<List<BusLine.LineData>>(var lines) -> {
                    log.debug("Term '{}' resolved {} line(s) before tl filter", entry.getKey(), lines.size());
                    lines.stream()
                            .filter(line -> entry.getValue().contains(line.tl()))
                            .forEach(line -> lineCodes.add(line.cl()));
                }
            }
        }

        var result = PublishResult.EMPTY;
        for (var cl : lineCodes) {
            log.debug("Fetching positions for lineCode={}", cl);
            switch (olhoVivoPort.positionsByLine(cl)) {
                case FetchResult.Unavailable<VehiclePosition.PositionResponse> _ -> unavailable++;
                case FetchResult.Fetched<VehiclePosition.PositionResponse>(var pos) -> {
                    if (pos != null && pos.vs() != null && !pos.vs().isEmpty()) {
                        log.info("Streaming {} vehicle(s) hr={}", pos.vs().size(), pos.hr());
                        result = result.plus(busStreamingPort.publishPositions(pos));
                    }
                }
            }
        }
        return result.plus(PublishResult.failedOf(unavailable));
    }

    /**
     * Delegates line search to the domain port.
     * @param terms search term (e.g. "178L")
     */
    public List<BusLine.LineData> searchLines(String terms) {
        return olhoVivoPort.searchLines(terms).orElse(List.of());
    }

    /**
     * Delegates vehicle position lookup to the domain port.
     * @param lineCode line code ({@code cl}) from OlhoVivo
     */
    public VehiclePosition.PositionResponse positionsByLine(int lineCode) {
        return olhoVivoPort.positionsByLine(lineCode).orElse(VehiclePosition.PositionResponse.empty());
    }
}
