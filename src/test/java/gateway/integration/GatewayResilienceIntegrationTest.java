package gateway.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.concurrent.Callable;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GatewayResilienceIntegrationTest {

    static WireMockServer wiremock = new WireMockServer(options().dynamicPort());

    static {
        wiremock.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("wiremock.server.port", wiremock::port);
    }

    @LocalServerPort
    private int localPort;

    private WebTestClient webClient;

    @BeforeEach
    void setUp() {
        webClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + localPort)
                .build();
    }

    @AfterEach
    void tearDown() {
        wiremock.resetAll();
    }

    @Test
    @Order(1)
    void shouldReturn200ForSuccessfulUpstreamCall() {
        wiremock.stubFor(get(urlPathMatching("/v4/.*"))
                .willReturn(aResponse().withStatus(200).withBody("OK")));

        webClient.get().uri("/api/v4/test").exchange().expectStatus().isOk();
    }

    @Test
    @Order(2)
    void shouldRetryOnServerError() {
        wiremock.stubFor(get(urlPathMatching("/v2/.*")).willReturn(aResponse().withStatus(500)));

        webClient.get().uri("/api/v2/test").exchange().expectStatus().is5xxServerError();

        int callCount = wiremock.getAllServeEvents().size();
        assert callCount >= 2 : "Expected at least 2 calls due to retry but got " + callCount;
    }

    @Test
    @Order(3)
    void shouldNotRetryOn4xx() {
        wiremock.stubFor(get(urlPathMatching("/v2/.*")).willReturn(aResponse().withStatus(400)));

        webClient.get().uri("/api/v2/test").exchange().expectStatus().isBadRequest();

        assert wiremock.getAllServeEvents().size() == 1
                : "Expected exactly 1 call but got "
                        + wiremock.getAllServeEvents().size();
    }

    @Test
    @Order(4)
    void shouldNotRetryOnPost() {
        wiremock.stubFor(post(urlPathMatching("/v2/.*")).willReturn(aResponse().withStatus(500)));

        webClient.post().uri("/api/v2/test").exchange().expectStatus().is5xxServerError();

        assert wiremock.getAllServeEvents().size() == 1
                : "Expected exactly 1 POST call but got "
                        + wiremock.getAllServeEvents().size();
    }

    @Test
    @Order(5)
    void shouldReturn504OnTimeout() {
        wiremock.stubFor(get(urlPathMatching("/v3/.*")).willReturn(aResponse().withFixedDelay(3000)));

        webClient.get().uri("/api/v3/test").exchange().expectStatus().isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    }

    @Test
    @Order(6)
    void shouldOpenCircuitBreakerAndRecover() {
        wiremock.stubFor(get(urlPathMatching("/v4/.*")).willReturn(aResponse().withStatus(500)));

        for (int i = 0; i < 3; i++) {
            webClient.get().uri("/api/v4/test").exchange().expectStatus().is5xxServerError();
        }

        webClient.get().uri("/api/v4/test").exchange().expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        wiremock.resetAll();
        wiremock.stubFor(get(urlPathMatching("/v4/.*"))
                .willReturn(aResponse().withStatus(200).withBody("OK")));

        await().atMost(5, SECONDS).until(upstreamOk());

        webClient.get().uri("/api/v4/test").exchange().expectStatus().isOk();

        webClient.get().uri("/api/v4/test").exchange().expectStatus().isOk();
    }

    @Test
    @Order(7)
    void shouldReturnFallbackResponseWithCorrelationId() {
        wiremock.stubFor(get(urlPathMatching("/v4/.*")).willReturn(aResponse().withStatus(500)));

        for (int i = 0; i < 3; i++) {
            webClient.get().uri("/api/v4/test").exchange();
        }

        webClient
                .get()
                .uri("/api/v4/test")
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(503)
                .jsonPath("$.error")
                .isEqualTo("Service Unavailable")
                .jsonPath("$.route")
                .isEqualTo("cb-test-route")
                .jsonPath("$.correlationId")
                .isNotEmpty()
                .jsonPath("$.timestamp")
                .isNotEmpty();

        wiremock.resetAll();
        wiremock.stubFor(get(urlPathMatching("/v4/.*"))
                .willReturn(aResponse().withStatus(200).withBody("OK")));

        await().atMost(5, SECONDS).until(upstreamOk());

        webClient.get().uri("/api/v4/test").exchange().expectStatus().isOk();
    }

    private Callable<Boolean> upstreamOk() {
        return () -> {
            try {
                webClient.get().uri("/api/v4/test").exchange().expectStatus().isOk();
                return true;
            } catch (Throwable e) {
                return false;
            }
        };
    }
}
