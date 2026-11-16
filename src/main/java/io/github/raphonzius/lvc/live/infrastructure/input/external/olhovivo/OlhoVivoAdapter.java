package io.github.raphonzius.lvc.live.infrastructure.input.external.olhovivo;

import io.github.raphonzius.lvc.live.domain.FetchResult;
import io.github.raphonzius.lvc.live.domain.bus.BusLine;
import io.github.raphonzius.lvc.live.domain.bus.OlhoVivoPort;
import io.github.raphonzius.lvc.live.domain.bus.VehiclePosition;
import io.github.raphonzius.lvc.live.infrastructure.input.external.UpstreamTelemetry;
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

    private static final String API = "olhovivo";

    private final OlhoVivoFeignClient feignClient;
    private final UpstreamTelemetry telemetry;

    @Override
    @CircuitBreaker(name = "olhovivo-api", fallbackMethod = "searchLinesFallback")
    public FetchResult<List<BusLine.LineData>> searchLines(String terms) {
        log.debug("Searching OlhoVivo lines: terms={}", terms);
        var lines = feignClient.searchLines(terms);
        return FetchResult.of(lines == null ? List.of() : lines);
    }

    /** Circuit breaker fallback for {@link #searchLines} — {@code Unavailable}, not an empty list. */
    public FetchResult<List<BusLine.LineData>> searchLinesFallback(String terms, Exception ex) {
        return FetchResult.unavailable(telemetry.fallback(API, ex));
    }

    @Override
    @CircuitBreaker(name = "olhovivo-api", fallbackMethod = "positionsByLineFallback")
    public FetchResult<VehiclePosition.PositionResponse> positionsByLine(int lineCode) {
        log.debug("Fetching OlhoVivo positions for lineCode={}", lineCode);
        var positions = feignClient.positionsByLine(lineCode);
        return FetchResult.of(positions);
    }

    /** Circuit breaker fallback for {@link #positionsByLine} — {@code Unavailable}, not an empty response. */
    public FetchResult<VehiclePosition.PositionResponse> positionsByLineFallback(int lineCode, Exception ex) {
        return FetchResult.unavailable(telemetry.fallback(API, ex));
    }
}
