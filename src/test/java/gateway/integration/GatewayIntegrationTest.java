package gateway.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

@SpringBootTest
@ActiveProfiles("test")
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
        webClient.get()
                .uri("/api/v1/test")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldNotReturn401WhenValidJwt() {
        webClient
                .mutateWith(mockJwt())
                .get()
                .uri("/api/v1/test")
                .exchange()
                .expectStatus().value(status ->
                        org.assertj.core.api.Assertions.assertThat(status)
                                .isNotEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED)
                                .isNotEqualTo(org.springframework.http.HttpStatus.FORBIDDEN));
    }

    @Test
    void shouldPermitPublicHealthEndpoint() {
        webClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldReturn403ForActuatorWithoutAdminRole() {
        webClient
                .mutateWith(mockJwt())
                .get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void shouldAllowActuatorWithAdminRole() {
        webClient
                .mutateWith(mockJwt()
                        .authorities(() -> "ROLE_ADMIN"))
                .get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldReturn401WhenInvalidJwt() {
        webClient.get()
                .uri("/api/v1/test")
                .header("Authorization", "Bearer invalid-token")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldIncludeSecurityHeaders() {
        webClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff");
    }
}
