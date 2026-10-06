package io.github.raphonzius.lvc.live.application.service;

import io.github.raphonzius.lvc.live.domain.bus.BusLine;
import io.github.raphonzius.lvc.live.domain.bus.BusPositionPersistencePort;
import io.github.raphonzius.lvc.live.domain.bus.OlhoVivoPort;
import io.github.raphonzius.lvc.live.domain.bus.VehiclePosition;
import io.github.raphonzius.lvc.live.infrastructure.config.properties.OlhoVivoProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Application service for OlhoVivo SPTrans bus data orchestration.
 *
 * Targeted polling workflow:
 *   1. For each configured lineTerms → /Linha/Buscar → resolve cl codes
 *   2. For each cl → /Posicao/Linha → vehicle positions
 *   3. Persist each returned vehicle position in PostgreSQL
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OlhoVivoService {

    private final OlhoVivoPort olhoVivoPort;
    private final BusPositionPersistencePort busPositionPersistencePort;
    private final OlhoVivoProperties properties;

    /**
     * Executes the targeted polling workflow:
     * searches configured line terms → filters by {@code tl} → fetches and persists positions per line.
     */
    public void fetchAndPersistVehiclePositions() {
        Map<String, List<Integer>> lineTerms = properties.polling().lineTerms();

        if (lineTerms.isEmpty()) {
            log.warn("No olhovivo.polling.line-terms configured — skipping poll");
            return;
        }

        Map<Integer, PollingLine> linesToPoll = lineTerms.entrySet().stream()
                .flatMap(entry -> {
                    List<BusLine.LineData> lines = olhoVivoPort.searchLines(entry.getKey());
                    log.debug("Term '{}' resolved {} line(s) before tl filter", entry.getKey(), lines.size());
                    return lines.stream()
                            .filter(line -> entry.getValue().contains(line.tl()));
                })
                .map(line -> new PollingLine(line.cl(), line.lt() + "-" + line.tl()))
                .collect(Collectors.toMap(
                        PollingLine::lineCode,
                        Function.identity(),
                        (first, duplicate) -> first,
                        LinkedHashMap::new
                ));

        linesToPoll.values().forEach(line -> {
            log.debug("Fetching positions for lineCode={} ({})", line.lineCode(), line.lineSign());
            VehiclePosition.PositionResponse pos = olhoVivoPort.positionsByLine(line.lineCode());
            if (pos == null || pos.vs() == null || pos.vs().isEmpty()) {
                return;
            }
            log.info("Persisting {} vehicle(s) for line={} ({}) hr={}",
                    pos.vs().size(), line.lineCode(), line.lineSign(), pos.hr());
            busPositionPersistencePort.savePositions(
                    line.lineCode(), line.lineSign(), pos.hr(), pos.vs());
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

    private record PollingLine(int lineCode, String lineSign) {}
}
