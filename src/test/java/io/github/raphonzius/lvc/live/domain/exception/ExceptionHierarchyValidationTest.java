package io.github.raphonzius.lvc.live.domain.exception;

import io.github.raphonzius.lvc.live.application.exception.ApplicationException;
import io.github.raphonzius.lvc.live.application.exception.InvalidRangeException;
import io.github.raphonzius.lvc.live.infrastructure.exception.InfrastructureException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * The exception roots validate their status argument in the constructor prologue,
 * before {@code super(...)} runs (JEP 513 — flexible constructor bodies).
 */
class ExceptionHierarchyValidationTest {

    @Test
    void domainExceptionRejectsNonErrorStatus() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DomainException("bad", 200))
                .withMessageContaining("200");
    }

    @Test
    void applicationExceptionRejectsNonErrorStatus() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ApplicationException("bad", new RuntimeException(), 99));
    }

    @Test
    void infrastructureExceptionRequiresHttpStatus() {
        assertThatNullPointerException()
                .isThrownBy(() -> new InfrastructureException("bad", null));
    }

    @Test
    void subtypesKeepTheirDeclaredStatus() {
        assertThat(new VesselException("v").statusCode()).isEqualTo(422);
        assertThat(new InvalidRangeException("r").statusCode()).isEqualTo(400);
        assertThat(new InfrastructureException("i", HttpStatus.BAD_GATEWAY).httpStatus())
                .isEqualTo(HttpStatus.BAD_GATEWAY);
    }
}
