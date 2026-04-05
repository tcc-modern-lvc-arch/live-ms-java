package io.github.raphonzius.lvc.live.infrastructure.input.external.olhovivo;

import io.github.raphonzius.lvc.live.domain.bus.BusLine;
import io.github.raphonzius.lvc.live.domain.bus.OlhoVivoPort;
import io.github.raphonzius.lvc.live.domain.bus.VehiclePosition;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * OlhoVivo adapter implementing OlhoVivoPort.
 * Wraps the Feign client with circuit breaker and fallback logic.
 *
 * Location: infrastructure/input/external/olhovivo/
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OlhoVivoAdapter implements OlhoVivoPort {

    private final OlhoVivoFeignClient feignClient;

    @Override
    @CircuitBreaker(name = "olhovivo-api", fallbackMethod = "searchLinesFallback")
    public List<BusLine.LineData> searchLines(String terms) {
        log.debug("Searching OlhoVivo lines: terms={}", terms);
        return feignClient.searchLines(terms);
    }

    public List<BusLine.LineData> searchLinesFallback(String terms, Exception ex) {
        log.warn("OlhoVivo searchLines circuit open: terms={}", terms, ex);
        return BusLine.LineData.empty();
    }

    @Override
    @CircuitBreaker(name = "olhovivo-api", fallbackMethod = "positionsByLineFallback")
    public VehiclePosition.PositionResponse positionsByLine(int lineCode) {
        log.debug("Fetching OlhoVivo positions for lineCode={}", lineCode);
        return feignClient.positionsByLine(lineCode);
    }

    public VehiclePosition.PositionResponse positionsByLineFallback(int lineCode, Exception ex) {
        log.warn("OlhoVivo positionsByLine circuit open: lineCode={}", lineCode, ex);
        return VehiclePosition.PositionResponse.empty();
    }
}
