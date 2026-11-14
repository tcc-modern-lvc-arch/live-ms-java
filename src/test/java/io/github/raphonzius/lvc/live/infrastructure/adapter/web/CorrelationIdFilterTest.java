package io.github.raphonzius.lvc.live.infrastructure.adapter.web;

import io.github.raphonzius.lvc.live.infrastructure.observability.CorrelationId;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    private record Seen(String scoped, String mdc) {
    }

    private Seen run(MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
        var seen = new AtomicReference<Seen>();
        filter.doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seen.set(new Seen(CorrelationId.current().orElse(null), MDC.get(CorrelationId.MDC_KEY)));
            }
        });
        return seen.get();
    }

    @Test
    void usesInboundHeaderAndEchoesItInTheResponse() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/vessels/poll");
        request.addHeader("X-Correlation-Id", "caller-123");
        var response = new MockHttpServletResponse();

        var seen = run(request, response);

        assertThat(seen).isEqualTo(new Seen("caller-123", "caller-123"));
        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo("caller-123");
    }

    @Test
    void generatesAnIdWhenAbsent() throws Exception {
        var response = new MockHttpServletResponse();

        var seen = run(new MockHttpServletRequest("GET", "/api/vessels/health"), response);

        assertThat(seen.scoped()).isNotBlank().isEqualTo(seen.mdc());
        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo(seen.scoped());
    }

    @Test
    void replacesUnsafeInboundValuesInsteadOfLoggingThem() throws Exception {
        var request = new MockHttpServletRequest("GET", "/x");
        request.addHeader("X-Correlation-Id", "bad id\nforged log line");
        var response = new MockHttpServletResponse();

        var seen = run(request, response);

        assertThat(seen.scoped()).doesNotContain("forged").matches("[A-Za-z0-9._-]{1,64}");
        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo(seen.scoped());
    }

    @Test
    void scopeEndsWithTheRequest() throws Exception {
        run(new MockHttpServletRequest("GET", "/x"), new MockHttpServletResponse());

        assertThat(CorrelationId.current()).isEmpty();
        assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
    }

    @Test
    void checkedExceptionsOfTheChainPropagateUnchanged() {
        var request = new MockHttpServletRequest("GET", "/x");
        var response = new MockHttpServletResponse();

        assertThatExceptionOfType(IOException.class).isThrownBy(() ->
                filter.doFilter(request, response, (_, _) -> {
                    throw new IOException("socket closed");
                })).withMessage("socket closed");
        assertThatExceptionOfType(ServletException.class).isThrownBy(() ->
                filter.doFilter(request, response, (_, _) -> {
                    throw new ServletException("boom");
                })).withMessage("boom");
    }
}
