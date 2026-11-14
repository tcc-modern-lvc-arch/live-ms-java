package io.github.raphonzius.lvc.live.infrastructure.input.external.ais;

import io.github.raphonzius.lvc.live.domain.FetchResult;
import io.github.raphonzius.lvc.live.infrastructure.input.external.UpstreamTelemetry;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The AIS Friends HTTP call behind the {@code ais-api} circuit breaker.
 *
 * <p>Separate from {@link AisAdapter} on purpose: the breaker must protect only the network
 * call. Mapping the payload inside it would let one bad record throw, hit the fallback, hide
 * every vessel and count against the breaker (and a self-invocation inside the adapter would
 * bypass the Spring proxy anyway). Circuit breaker {@code ais-api} is configured in
 * application.yaml (resilience4j).</p>
 */
@Component
@RequiredArgsConstructor
public class AisUpstream {

    static final String API = "ais";

    private final AisFeignClient feignClient;
    private final UpstreamTelemetry telemetry;

    @CircuitBreaker(name = "ais-api", fallbackMethod = "fetchFallback")
    public FetchResult<List<AisVesselResponse>> fetch(
            double lonMin, double latMin, double lonMax, double latMax, int zoom) {
        var response = feignClient.getVessels(lonMin, latMin, lonMax, latMax, zoom);
        telemetry.success(API);
        return FetchResult.of(response == null ? List.of() : response);
    }

    /** Circuit-breaker fallback (open circuit or call failure): reported as unavailable, not as "no vessels". */
    public FetchResult<List<AisVesselResponse>> fetchFallback(
            double lonMin, double latMin, double lonMax, double latMax, int zoom, Exception exception) {
        return FetchResult.unavailable(telemetry.fallback(API, exception));
    }
}
