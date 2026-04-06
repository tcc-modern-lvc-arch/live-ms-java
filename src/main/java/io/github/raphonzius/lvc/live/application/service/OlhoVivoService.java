package io.github.raphonzius.lvc.live.application.service;

import io.github.raphonzius.lvc.live.domain.bus.BusLine;
import io.github.raphonzius.lvc.live.domain.bus.OlhoVivoPort;
import io.github.raphonzius.lvc.live.domain.bus.VehiclePosition;
import io.github.raphonzius.lvc.live.domain.streaming.BusStreamingPort;
import io.github.raphonzius.lvc.live.infrastructure.config.properties.OlhoVivoProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Application service for OlhoVivo SPTrans bus data orchestration.
 *
 * Targeted polling workflow:
 *   1. For each configured lineTerms → /Linha/Buscar → resolve cl codes
 *   2. For each cl → /Posicao/Linha → vehicle positions
 *   3. Publish each PositionResponse to Redis buses:stream
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
     * searches configured line terms → filters by {@code tl} → fetches positions per line → publishes to Redis.
     */
    public void fetchAndStreamVehiclePositions() {
        Map<String, List<Integer>> lineTerms = properties.polling().lineTerms();

        if (lineTerms.isEmpty()) {
            log.warn("No olhovivo.polling.line-terms configured — skipping poll");
            return;
        }

        lineTerms.entrySet().stream()
                .flatMap(entry -> {
                    List<BusLine.LineData> lines = olhoVivoPort.searchLines(entry.getKey());
                    log.debug("Term '{}' resolved {} line(s) before tl filter", entry.getKey(), lines.size());
                    return lines.stream()
                            .filter(line -> entry.getValue().contains(line.tl()));
                })
                .mapToInt(BusLine.LineData::cl)
                .distinct()
                .mapToObj(cl -> {
                    log.debug("Fetching positions for lineCode={}", cl);
                    return olhoVivoPort.positionsByLine(cl);
                })
                .filter(pos -> pos != null && pos.vs() != null && !pos.vs().isEmpty())
                .forEach(pos -> {
                    log.info("Streaming {} vehicle(s) hr={}", pos.vs().size(), pos.hr());
                    busStreamingPort.publishPositions(pos);
                });
    }

    /**
     * Delegates line search to the domain port.
     * @param terms search term (e.g. "178L")
     */
    public List<BusLine.LineData> searchLines(String terms) {
        return olhoVivoPort.searchLines(terms);
    }

    /**
     * Delegates vehicle position lookup to the domain port.
     * @param lineCode line code ({@code cl}) from OlhoVivo
     */
    public VehiclePosition.PositionResponse positionsByLine(int lineCode) {
        return olhoVivoPort.positionsByLine(lineCode);
    }
}
