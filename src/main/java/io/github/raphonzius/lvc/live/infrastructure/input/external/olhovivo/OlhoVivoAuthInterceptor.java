package io.github.raphonzius.lvc.live.infrastructure.input.external.olhovivo;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import io.github.raphonzius.lvc.live.infrastructure.config.properties.OlhoVivoProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Feign RequestInterceptor that handles OlhoVivo cookie-based session auth.
 *
 * OlhoVivo requires POST /Login/Autenticar?token={token} before any API call.
 * The response sets a session cookie (JSESSIONID) that must be sent on every
 * subsequent request. This interceptor manages authentication lazily and
 * re-authenticates automatically when the session expires.
 */
@Slf4j
public class OlhoVivoAuthInterceptor implements RequestInterceptor {

    private static final String BASE_URL = "https://api.olhovivo.sptrans.com.br/v2.1";

    private final String token;
    private final RestClient restClient;

    private volatile String sessionCookie;
    private final AtomicBoolean authenticated = new AtomicBoolean(false);

    public OlhoVivoAuthInterceptor(OlhoVivoProperties properties) {
        this.token = properties.token();
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .build();
    }

    @Override
    public void apply(RequestTemplate template) {
        if (!authenticated.get()) {
            authenticate();
        }
        if (sessionCookie != null) {
            template.header("Cookie", sessionCookie);
        }
    }

    /**
     * Performs POST /Login/Autenticar?token={token}, captures the Set-Cookie header.
     * Thread-safe: only one thread authenticates at a time.
     */
    public synchronized void authenticate() {
        if (authenticated.get()) {
            return;
        }
        try {
            log.info("Authenticating with OlhoVivo API...");
            ResponseEntity<Boolean> response = restClient.post()
                    .uri("/Login/Autenticar?token={token}", token)
                    .retrieve()
                    .toEntity(Boolean.class);

            Boolean success = response.getBody();
            if (Boolean.TRUE.equals(success)) {
                List<String> cookies = response.getHeaders().get("Set-Cookie");
                if (cookies != null && !cookies.isEmpty()) {
                    sessionCookie = cookies.getFirst().split(";")[0];
                    authenticated.set(true);
                    log.info("OlhoVivo authentication successful");
                } else {
                    log.warn("OlhoVivo auth returned true but no Set-Cookie header found");
                }
            } else {
                log.error("OlhoVivo authentication failed — token may be invalid");
            }
        } catch (Exception e) {
            log.error("OlhoVivo authentication request failed", e);
        }
    }

    /**
     * Forces re-authentication on the next request.
     * Call this when a 401 is received.
     */
    public void invalidateSession() {
        authenticated.set(false);
        sessionCookie = null;
        log.info("OlhoVivo session invalidated — will re-authenticate on next request");
    }
}
