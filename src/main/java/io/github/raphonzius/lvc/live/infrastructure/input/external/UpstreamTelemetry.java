package io.github.raphonzius.lvc.live.infrastructure.input.external;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Metrics and logs shared by the upstream adapters (AIS, CGESP, OlhoVivo).
 *
 * <ul>
 *   <li>{@value #FALLBACK_METRIC}{@code {api,reason}} counts every circuit-breaker fallback; an open
 *       circuit is logged at WARN without a stack, any other failure at ERROR with it;</li>
 *   <li>{@value #INVALID_METRIC}{@code {api}} counts upstream records dropped as unusable (each one
 *       also logged at WARN).</li>
 * </ul>
 */
@Slf4j
@Component
public class UpstreamTelemetry {

    public static final String FALLBACK_METRIC = "lvc.upstream.fallback";
    public static final String INVALID_METRIC = "lvc.upstream.invalid_records";

    /** Reason tag used when the circuit breaker rejected the call without trying it. */
    public static final String CIRCUIT_OPEN = "circuit_open";

    private final MeterRegistry registry;

    @Autowired
    public UpstreamTelemetry(ObjectProvider<MeterRegistry> registry) {
        this(registry.getIfAvailable(() -> Metrics.globalRegistry));
    }

    public UpstreamTelemetry(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Records a fallback of {@code api} caused by {@code error}.
     *
     * @return the short reason (also the metric tag), for {@code FetchResult.Unavailable}
     */
    public String fallback(String api, Throwable error) {
        var reason = error instanceof CallNotPermittedException
                ? CIRCUIT_OPEN : error.getClass().getSimpleName();
        registry.counter(FALLBACK_METRIC, "api", api, "reason", reason).increment();

        if (error instanceof CallNotPermittedException) {
            log.warn("[{}] circuit breaker open — upstream call skipped", api);
        } else {
            log.error("[{}] upstream call failed — fallback ({})", api, reason, error);
        }
        return reason;
    }

    /** Counts and warns about an unusable upstream record. */
    public void invalidRecord(String api, String detail) {
        registry.counter(INVALID_METRIC, "api", api).increment();
        log.warn("[{}] dropping invalid upstream record ({})", api, detail);
    }
}
