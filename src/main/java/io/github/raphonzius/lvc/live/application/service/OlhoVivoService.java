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

    public void fetchAndStreamVehiclePositions() {
        List<String> terms = properties.polling().lineTerms();

        if (terms.isEmpty()) {
            log.warn("No olhovivo.polling.line-terms configured — skipping poll");
            return;
        }

        terms.stream()
                .flatMap(term -> {
                    List<BusLine.LineData> lines = olhoVivoPort.searchLines(term);
                    log.debug("Term '{}' resolved {} line(s)", term, lines.size());
                    return lines.stream();
                })
                .mapToInt(line -> switch (line) {
                    case BusLine.LineData(var cl, var lc, var lt, var sl, var tl, var tp, var ts) -> cl;
                })
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

    public List<BusLine.LineData> searchLines(String terms) {
        return olhoVivoPort.searchLines(terms);
    }

    public VehiclePosition.PositionResponse positionsByLine(int lineCode) {
        return olhoVivoPort.positionsByLine(lineCode);
    }
}
