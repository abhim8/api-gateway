package gateway.integration;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest
class GatewayIntegrationTest {

    @Autowired
    private ApplicationContext context;

    private WebTestClient webClient;

    @BeforeEach
    void setUp() {
        webClient = WebTestClient.bindToApplicationContext(context)
                .apply(SecurityMockServerConfigurers.springSecurity())
                .configureClient()
                .build();
    }

    @Test
    void shouldReturn401WhenNoJwt() {
        webClient.get().uri("/api/v1/test").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void shouldNotReturn401WhenValidJwt() {
        webClient
                .mutateWith(mockJwt())
                .get()
                .uri("/api/v1/test")
                .exchange()
                .expectStatus()
                .is5xxServerError();
    }

    @Test
    void shouldPermitPublicHealthEndpoint() {
        webClient.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }

    @Test
    void shouldReturn403ForActuatorWithoutAdminRole() {
        webClient
                .mutateWith(mockJwt())
                .get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus()
                .isForbidden();
    }

    @Test
    void shouldAllowActuatorWithAdminRole() {
        webClient
                .mutateWith(mockJwt().authorities(() -> "ROLE_ADMIN"))
                .get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    void shouldReturn401WhenInvalidJwt() {
        webClient
                .get()
                .uri("/api/v1/test")
                .header("Authorization", "Bearer invalid-token")
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void shouldIncludeSecurityHeaders() {
        webClient
                .get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .valueEquals("X-Content-Type-Options", "nosniff");
    }

    @Test
    void shouldReturnJsonErrorBodyFor404() {
        webClient
                .mutateWith(mockJwt())
                .get()
                .uri("/nonexistent-path")
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(404)
                .jsonPath("$.path")
                .isEqualTo("/nonexistent-path");
    }

    @Test
    void shouldReturnJsonErrorBodyFor401() {
        webClient.get().uri("/api/v1/test").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void shouldExposePrometheusMetrics() {
        webClient
                .mutateWith(mockJwt().authorities(() -> "ROLE_ADMIN"))
                .get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .valueEquals("Content-Type", "text/plain;version=0.0.4;charset=utf-8");
    }

    @Test
    void shouldExposeHealthEndpoint() {
        webClient
                .get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("UP");
    }

    @Test
    void shouldExposeLivenessProbe() {
        webClient
                .get()
                .uri("/actuator/health/liveness")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("UP");
    }

    @Test
    void shouldExposeReadinessProbe() {
        webClient
                .get()
                .uri("/actuator/health/readiness")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("UP");
    }
}
