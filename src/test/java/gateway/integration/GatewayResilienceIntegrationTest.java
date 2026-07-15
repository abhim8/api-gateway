package gateway.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.util.Date;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GatewayResilienceIntegrationTest {

    private static final String JWT_ISSUER = "https://test-issuer.example.com";

    static WireMockServer wiremock = new WireMockServer(options().dynamicPort());

    static RSAKey rsaKey;

    static {
        try {
            wiremock.start();
            rsaKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
            serveJwks();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void serveJwks() throws Exception {
        JWKSet jwkSet = new JWKSet(rsaKey.toPublicJWK());
        wiremock.stubFor(get(urlPathEqualTo("/.well-known/jwks.json"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(JSONObjectUtils.toJSONString(jwkSet.toJSONObject()))));
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("wiremock.server.port", wiremock::port);
        registry.add(
                "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://localhost:" + wiremock.port() + "/.well-known/jwks.json");
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> JWT_ISSUER);
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
        try {
            serveJwks();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String createJwt() throws Exception {
        RSASSASigner signer = new RSASSASigner(rsaKey);
        Date now = new Date();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("test-user")
                .issuer(JWT_ISSUER)
                .issueTime(now)
                .expirationTime(new Date(now.getTime() + 3600_000))
                .build();
        SignedJWT signedJwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), claims);
        signedJwt.sign(signer);
        return signedJwt.serialize();
    }

    @Test
    @Order(1)
    void shouldReturn200ForSuccessfulUpstreamCall() throws Exception {
        wiremock.stubFor(get(urlPathMatching("/v4/.*"))
                .willReturn(aResponse().withStatus(200).withBody("OK")));

        webClient
                .get()
                .uri("/api/v4/test")
                .header("Authorization", "Bearer " + createJwt())
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    @Order(2)
    void shouldRetryOnServerError() throws Exception {
        wiremock.stubFor(get(urlPathMatching("/v2/.*")).willReturn(aResponse().withStatus(500)));

        String jwt = createJwt();
        webClient
                .get()
                .uri("/api/v2/test")
                .header("Authorization", "Bearer " + jwt)
                .exchange()
                .expectStatus()
                .is5xxServerError();

        int callCount = wiremock.getAllServeEvents().size();
        assert callCount >= 2 : "Expected at least 2 calls due to retry but got " + callCount;
    }

    @Test
    @Order(3)
    void shouldNotRetryOn4xx() throws Exception {
        wiremock.stubFor(get(urlPathMatching("/v2/.*")).willReturn(aResponse().withStatus(400)));

        String jwt = createJwt();
        webClient
                .get()
                .uri("/api/v2/test")
                .header("Authorization", "Bearer " + jwt)
                .exchange()
                .expectStatus()
                .isBadRequest();

        assert wiremock.getAllServeEvents().size() == 1
                : "Expected exactly 1 call but got "
                        + wiremock.getAllServeEvents().size();
    }

    @Test
    @Order(4)
    void shouldNotRetryOnPost() throws Exception {
        wiremock.stubFor(post(urlPathMatching("/v2/.*")).willReturn(aResponse().withStatus(500)));

        String jwt = createJwt();
        webClient
                .post()
                .uri("/api/v2/test")
                .header("Authorization", "Bearer " + jwt)
                .exchange()
                .expectStatus()
                .is5xxServerError();

        assert wiremock.getAllServeEvents().size() == 1
                : "Expected exactly 1 POST call but got "
                        + wiremock.getAllServeEvents().size();
    }

    @Test
    @Order(5)
    void shouldReturn504OnTimeout() throws Exception {
        wiremock.stubFor(get(urlPathMatching("/v3/.*")).willReturn(aResponse().withFixedDelay(3000)));

        String jwt = createJwt();
        webClient
                .get()
                .uri("/api/v3/test")
                .header("Authorization", "Bearer " + jwt)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    }

    @Test
    @Order(6)
    void shouldOpenCircuitBreakerAndRecover() throws Exception {
        wiremock.stubFor(get(urlPathMatching("/v4/.*")).willReturn(aResponse().withStatus(500)));

        String jwt = createJwt();
        for (int i = 0; i < 3; i++) {
            webClient
                    .get()
                    .uri("/api/v4/test")
                    .header("Authorization", "Bearer " + jwt)
                    .exchange()
                    .expectStatus()
                    .is5xxServerError();
        }

        webClient
                .get()
                .uri("/api/v4/test")
                .header("Authorization", "Bearer " + jwt)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        wiremock.resetAll();
        serveJwks();
        wiremock.stubFor(get(urlPathMatching("/v4/.*"))
                .willReturn(aResponse().withStatus(200).withBody("OK")));

        await().atMost(5, SECONDS).until(upstreamOk(jwt));

        webClient
                .get()
                .uri("/api/v4/test")
                .header("Authorization", "Bearer " + jwt)
                .exchange()
                .expectStatus()
                .isOk();

        webClient
                .get()
                .uri("/api/v4/test")
                .header("Authorization", "Bearer " + jwt)
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    @Order(7)
    void shouldReturnFallbackResponseWithCorrelationId() throws Exception {
        wiremock.stubFor(get(urlPathMatching("/v4/.*")).willReturn(aResponse().withStatus(500)));

        String jwt = createJwt();
        for (int i = 0; i < 3; i++) {
            webClient
                    .get()
                    .uri("/api/v4/test")
                    .header("Authorization", "Bearer " + jwt)
                    .exchange();
        }

        webClient
                .get()
                .uri("/api/v4/test")
                .header("Authorization", "Bearer " + jwt)
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
        serveJwks();
        wiremock.stubFor(get(urlPathMatching("/v4/.*"))
                .willReturn(aResponse().withStatus(200).withBody("OK")));

        await().atMost(5, SECONDS).until(upstreamOk(jwt));

        webClient
                .get()
                .uri("/api/v4/test")
                .header("Authorization", "Bearer " + jwt)
                .exchange()
                .expectStatus()
                .isOk();
    }

    private Callable<Boolean> upstreamOk(String jwt) {
        return () -> {
            try {
                webClient
                        .get()
                        .uri("/api/v4/test")
                        .header("Authorization", "Bearer " + jwt)
                        .exchange()
                        .expectStatus()
                        .isOk();
                return true;
            } catch (Throwable e) {
                return false;
            }
        };
    }
}
