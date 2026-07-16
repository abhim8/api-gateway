package gateway.web;

import gateway.common.util.HeaderConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

class FallbackControllerTest {

    private WebTestClient webClient;

    @BeforeEach
    void setUp() {
        webClient = WebTestClient.bindToController(new FallbackController()).build();
    }

    @Test
    void shouldReturn503WithCorrelationId() {
        webClient
                .get()
                .uri("/fallback?route=test-route")
                .header(HeaderConstants.X_CORRELATION_ID, "test-id-123")
                .exchange()
                .expectStatus()
                .isEqualTo(503)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(503)
                .jsonPath("$.error")
                .isEqualTo(FallbackController.ERROR_SERVICE_UNAVAILABLE)
                .jsonPath("$.route")
                .isEqualTo("test-route")
                .jsonPath("$.correlationId")
                .isEqualTo("test-id-123")
                .jsonPath("$.timestamp")
                .isNotEmpty();
    }

    @Test
    void shouldDefaultCorrelationIdWhenNotPresent() {
        webClient
                .get()
                .uri("/fallback?route=test-route")
                .exchange()
                .expectStatus()
                .isEqualTo(503)
                .expectBody()
                .jsonPath("$.correlationId")
                .isEqualTo(FallbackController.DEFAULT_UNKNOWN);
    }

    @Test
    void shouldDefaultRouteWhenNotProvided() {
        webClient
                .get()
                .uri("/fallback")
                .exchange()
                .expectStatus()
                .isEqualTo(503)
                .expectBody()
                .jsonPath("$.route")
                .isEqualTo(FallbackController.DEFAULT_UNKNOWN);
    }

    @Test
    void shouldSupportPathBasedRoute() {
        webClient
                .get()
                .uri("/fallback/path-route")
                .exchange()
                .expectStatus()
                .isEqualTo(503)
                .expectBody()
                .jsonPath("$.route")
                .isEqualTo("path-route");
    }
}
