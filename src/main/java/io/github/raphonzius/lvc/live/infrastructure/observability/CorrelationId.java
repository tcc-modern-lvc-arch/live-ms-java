package io.github.raphonzius.lvc.live.infrastructure.observability;

import org.slf4j.MDC;

import java.util.Optional;
import java.util.UUID;

/**
 * Correlation id of the current polling cycle, carried by a {@link ScopedValue} (JEP 506)
 * and mirrored into the SLF4J {@link MDC} so every log line in the scope can print it
 * ({@code logging.pattern.correlation} in application.yaml).
 *
 * <p>A ScopedValue binding is immutable and ends with its scope, so there is nothing to
 * leak between pooled or virtual threads. It is not inherited by tasks submitted to an
 * executor (that would need the still-preview structured concurrency API), so code that
 * fans out — e.g. {@code EventPublisher} — captures {@link #current()} on the caller and
 * re-binds it inside each task with {@link #runWith}.</p>
 */
public final class CorrelationId {

    /** MDC key under which the id is exposed to the log pattern. */
    public static final String MDC_KEY = "correlationId";

    private static final ScopedValue<String> CURRENT = ScopedValue.newInstance();

    private CorrelationId() {
    }

    /** A new short random id (12 hex chars) — enough to tell polling cycles apart in logs. */
    public static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    /** The id bound to the current scope, if any. */
    public static Optional<String> current() {
        return CURRENT.isBound() ? Optional.of(CURRENT.get()) : Optional.empty();
    }

    /**
     * Runs {@code action} with {@code id} bound both as the scoped value and in the MDC.
     * The previous MDC value (if any) is restored afterwards.
     */
    public static void runWith(String id, Runnable action) {
        ScopedValue.where(CURRENT, id).run(() -> {
            var previous = MDC.get(MDC_KEY);
            MDC.put(MDC_KEY, id);
            try {
                action.run();
            } finally {
                if (previous == null) {
                    MDC.remove(MDC_KEY);
                } else {
                    MDC.put(MDC_KEY, previous);
                }
            }
        });
    }
}
