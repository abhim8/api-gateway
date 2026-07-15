package gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest
class GatewayApplicationTests {

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
    void contextLoads() {}

    @Test
    void shouldReturn404ForUnknownRoute() {
        webClient
                .mutateWith(SecurityMockServerConfigurers.mockJwt())
                .get()
                .uri("/nonexistent")
                .exchange()
                .expectStatus()
                .isNotFound();
    }
}
