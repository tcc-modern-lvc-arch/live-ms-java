package io.github.raphonzius.lvc.live.infrastructure.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when logging in to the SPTrans OlhoVivo API fails (request error, token rejected, no
 * session cookie) or when a login is being skipped because the previous one failed less than
 * {@code olhovivo.login-cooldown} ago. Maps to {@link HttpStatus#BAD_GATEWAY}.
 */
public final class OlhoVivoAuthenticationException extends InfrastructureException {

    public OlhoVivoAuthenticationException(String message) {
        super(message, HttpStatus.BAD_GATEWAY);
    }

    public OlhoVivoAuthenticationException(String message, Throwable cause) {
        super(message, cause, HttpStatus.BAD_GATEWAY);
    }
}
