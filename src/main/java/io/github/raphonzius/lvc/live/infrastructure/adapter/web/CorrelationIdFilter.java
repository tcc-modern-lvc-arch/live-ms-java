package io.github.raphonzius.lvc.live.infrastructure.adapter.web;

import io.github.raphonzius.lvc.live.infrastructure.observability.CorrelationId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * Binds a correlation id for the whole servlet request, like the polling runner does for a cycle.
 *
 * <p>Uses the inbound {@value #HEADER} when it is a sane token (so a caller's id flows through),
 * otherwise generates one — never echoes arbitrary header content into logs. The id is returned in
 * the response header and bound via {@link CorrelationId#runWith} (ScopedValue + MDC), so every log
 * line of the request, including {@code GlobalExceptionHandler}'s, carries it.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";

    /** Inbound ids must be short and log-safe. */
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var inbound = request.getHeader(HEADER);
        var id = inbound != null && SAFE_ID.matcher(inbound).matches() ? inbound : CorrelationId.newId();
        response.setHeader(HEADER, id);

        // runWith takes a Runnable: carry the checked exceptions out and rethrow them unchanged.
        var failure = new AtomicReference<Exception>();
        CorrelationId.runWith(id, () -> {
            try {
                chain.doFilter(request, response);
            } catch (IOException | ServletException | RuntimeException e) {
                failure.set(e);
            }
        });
        switch (failure.get()) {
            case null -> { }
            case IOException e -> throw e;
            case ServletException e -> throw e;
            case RuntimeException e -> throw e;
            default -> throw new IllegalStateException(failure.get());
        }
    }
}
