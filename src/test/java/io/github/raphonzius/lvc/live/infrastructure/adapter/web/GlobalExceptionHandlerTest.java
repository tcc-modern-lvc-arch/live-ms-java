package io.github.raphonzius.lvc.live.infrastructure.adapter.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unknownRouteIsNotFoundNotInternalServerError() {
        var request = new MockHttpServletRequest("POST", "/api/v1/vessels/poll");
        var ex = new NoResourceFoundException(HttpMethod.POST, "/api/v1/vessels/poll", "api/v1/vessels/poll");

        var response = handler.handleUnexpected(ex, request);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getInstance()).hasToString("/api/v1/vessels/poll");
    }

    @Test
    void genuinelyUnexpectedExceptionIsInternalServerError() {
        var request = new MockHttpServletRequest("GET", "/api/vessels/health");

        var response = handler.handleUnexpected(new IllegalStateException("boom"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().getDetail()).isEqualTo("An unexpected error occurred");
    }
}
