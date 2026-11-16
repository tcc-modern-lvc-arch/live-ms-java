package io.github.raphonzius.lvc.live.infrastructure.input.external;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.raphonzius.lvc.live.infrastructure.config.properties.UpstreamProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics and log hygiene shared by the upstream adapters (AIS, CGESP, OlhoVivo).
 *
 * <ul>
 *   <li>{@value #FALLBACK_METRIC}{@code {api,reason}} counts every circuit-breaker fallback;</li>
 *   <li>{@value #INVALID_METRIC}{@code {api}} counts upstream records dropped as unusable;</li>
 *   <li>an open circuit is logged at WARN without a stack (it is expected and repeats every
 *       cycle); any other failure is logged at ERROR, with the stack only on the first occurrence
 *       after a success and then at most once per {@code logStackInterval} — an outage polled
 *       every 30 s must not flood the log with identical traces;</li>
 *   <li>invalid-record warnings are rate-limited the same way (the counter keeps the exact number).</li>
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
    private final Clock clock;
    private final long intervalNanos;

    /** api → last time a stack trace was logged ({@code Long.MIN_VALUE} = never / reset by success). */
    private final ConcurrentHashMap<String, AtomicLong> lastStack = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> lastInvalidWarn = new ConcurrentHashMap<>();

    @Autowired
    public UpstreamTelemetry(ObjectProvider<MeterRegistry> registry, UpstreamProperties properties) {
        this(registry.getIfAvailable(() -> Metrics.globalRegistry), Clock.systemUTC(), properties.logStackInterval());
    }

    public UpstreamTelemetry(MeterRegistry registry, Clock clock, Duration logStackInterval) {
        this.registry = registry;
        this.clock = clock;
        this.intervalNanos = logStackInterval.toNanos();
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
        } else if (due(lastStack, api)) {
            log.error("[{}] upstream call failed — fallback ({})", api, reason, error);
        } else {
            log.error("[{}] upstream call failed — fallback ({}): {}", api, reason, error.getMessage());
        }
        return reason;
    }

    /** Resets the stack-trace throttle: the next failure of {@code api} logs its stack again. */
    public void success(String api) {
        var last = lastStack.get(api);
        if (last != null) {
            last.set(Long.MIN_VALUE);
        }
    }

    /** Counts an unusable upstream record; warns at most once per interval per api. */
    public void invalidRecord(String api, String detail) {
        registry.counter(INVALID_METRIC, "api", api).increment();
        if (due(lastInvalidWarn, api)) {
            log.warn("[{}] dropping invalid upstream record ({}); further drops are only counted for {}",
                    api, detail, Duration.ofNanos(intervalNanos));
        } else {
            log.debug("[{}] dropping invalid upstream record ({})", api, detail);
        }
    }

    /** @return true (and arms the throttle) when nothing was logged for {@code key} within the interval */
    private boolean due(ConcurrentHashMap<String, AtomicLong> map, String key) {
        var last = map.computeIfAbsent(key, _ -> new AtomicLong(Long.MIN_VALUE));
        var now = nowNanos();
        while (true) {
            var prev = last.get();
            if (prev != Long.MIN_VALUE && now - prev < intervalNanos) {
                return false;
            }
            if (last.compareAndSet(prev, now)) {
                return true;
            }
        }
    }

    private long nowNanos() {
        var i = clock.instant();
        return i.getEpochSecond() * 1_000_000_000L + i.getNano();
    }
}
