package io.github.raphonzius.lvc.live.infrastructure.observability;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CorrelationId}: ScopedValue binding mirrored into the SLF4J MDC.
 */
class CorrelationIdTest {

    @Test
    void isUnboundOutsideAScope() {
        assertThat(CorrelationId.current()).isEmpty();
        assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
    }

    @Test
    void bindsScopedValueAndMdcInsideScopeAndClearsAfter() {
        var scoped = new AtomicReference<String>();
        var mdc = new AtomicReference<String>();

        CorrelationId.runWith("abc", () -> {
            scoped.set(CorrelationId.current().orElseThrow());
            mdc.set(MDC.get(CorrelationId.MDC_KEY));
        });

        assertThat(scoped).hasValue("abc");
        assertThat(mdc).hasValue("abc");
        assertThat(CorrelationId.current()).isEmpty();
        assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
    }

    @Test
    void nestedScopeRestoresOuterValue() {
        var afterInner = new AtomicReference<String>();

        CorrelationId.runWith("outer", () -> {
            CorrelationId.runWith("inner", () -> assertThat(MDC.get(CorrelationId.MDC_KEY)).isEqualTo("inner"));
            afterInner.set(MDC.get(CorrelationId.MDC_KEY) + "/" + CorrelationId.current().orElseThrow());
        });

        assertThat(afterInner).hasValue("outer/outer");
    }

    @Test
    void newIdIsShortAndUnique() {
        var a = CorrelationId.newId();
        var b = CorrelationId.newId();

        assertThat(a).hasSize(12).isNotEqualTo(b);
    }
}
