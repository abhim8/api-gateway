package gateway.auth;

import gateway.common.util.HeaderConstants;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class RemoteAuthenticationProviderTest {

    private static final DefaultDataBufferFactory BUFFER_FACTORY = new DefaultDataBufferFactory();

    @Test
    void shouldReturnAuthenticatedWithFullClaims() {
        String json = """
                {
                    "authenticated": true,
                    "subject": "user-123",
                    "username": "abhilash",
                    "email": "abhilash@example.com",
                    "roles": ["USER", "ADMIN"],
                    "permissions": ["template.read", "template.write"],
                    "expiresAt": "2026-12-31T23:59:59Z",
                    "metadata": { "tenantId": "tenant-001" }
                }
                """;

        RemoteAuthenticationProvider provider = providerWithJsonResponse(json, HttpStatus.OK);

        StepVerifier.create(provider.authenticate(exchange("Bearer test-token", "corr-123")))
                .assertNext(result -> {
                    assertTrue(result.authenticated());
                    assertEquals("user-123", result.subject());
                    assertEquals(List.of("USER", "ADMIN"), result.roles());
                    assertEquals(List.of("template.read", "template.write"), result.permissions());
                    assertEquals("abhilash", result.claims().get("username"));
                    assertEquals("abhilash@example.com", result.claims().get("email"));
                    assertEquals("tenant-001", result.claims().get("tenantId"));
                    assertEquals("2026-12-31T23:59:59Z", result.claims().get("expiresAt"));
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnAuthenticatedWithMinimalFields() {
        String json = """
                {
                    "authenticated": true,
                    "subject": "user-456"
                }
                """;

        RemoteAuthenticationProvider provider = providerWithJsonResponse(json, HttpStatus.OK);

        StepVerifier.create(provider.authenticate(exchange("Bearer test-token", "corr-456")))
                .assertNext(result -> {
                    assertTrue(result.authenticated());
                    assertEquals("user-456", result.subject());
                    assertTrue(result.roles().isEmpty());
                    assertTrue(result.permissions().isEmpty());
                    assertTrue(result.claims().isEmpty());
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnUnauthenticatedWhenTokenInactive() {
        String json = """
                {
                    "authenticated": false
                }
                """;

        RemoteAuthenticationProvider provider = providerWithJsonResponse(json, HttpStatus.OK);

        StepVerifier.create(provider.authenticate(exchange("Bearer test-token", null)))
                .assertNext(result -> {
                    assertFalse(result.authenticated());
                    assertNull(result.subject());
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnUnauthenticatedWhenNoBearerToken() {
        RemoteAuthenticationProvider provider = providerWithResponse(
                ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(Flux.just(BUFFER_FACTORY.wrap("{}".getBytes(StandardCharsets.UTF_8))))
                        .build());

        StepVerifier.create(provider.authenticate(exchange(null, "corr-123")))
                .assertNext(result -> assertFalse(result.authenticated()))
                .verifyComplete();
    }

    @Test
    void shouldReturnUnauthenticatedWhenInvalidBearerFormat() {
        RemoteAuthenticationProvider provider = providerWithResponse(
                ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(Flux.just(BUFFER_FACTORY.wrap("{}".getBytes(StandardCharsets.UTF_8))))
                        .build());

        StepVerifier.create(provider.authenticate(exchange("NotBearer token", "corr-123")))
                .assertNext(result -> assertFalse(result.authenticated()))
                .verifyComplete();
    }

    @Test
    void shouldReturnUnauthenticatedWhenHttp401() {
        RemoteAuthenticationProvider provider = providerWithResponse(
                ClientResponse.create(HttpStatus.UNAUTHORIZED)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .build());

        StepVerifier.create(provider.authenticate(exchange("Bearer invalid-token", "corr-123")))
                .assertNext(result -> {
                    assertFalse(result.authenticated());
                    assertNull(result.subject());
                })
                .verifyComplete();
    }

    @Test
    void shouldThrowServiceUnavailableWhenHttp500() {
        RemoteAuthenticationProvider provider = providerWithResponse(
                ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .build());

        StepVerifier.create(provider.authenticate(exchange("Bearer test-token", "corr-123")))
                .expectErrorSatisfies(error -> {
                    assertInstanceOf(ResponseStatusException.class, error);
                    assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                            ((ResponseStatusException) error).getStatusCode());
                })
                .verify();
    }

    @Test
    void shouldThrowServiceUnavailableWhenTimeout() {
        ExchangeFunction exchangeFunction = request -> Mono.error(new TimeoutException("Read timed out"));
        RemoteAuthenticationProvider provider = providerWithExchange(exchangeFunction);

        StepVerifier.create(provider.authenticate(exchange("Bearer test-token", "corr-123")))
                .expectErrorSatisfies(error -> {
                    assertInstanceOf(ResponseStatusException.class, error);
                    assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                            ((ResponseStatusException) error).getStatusCode());
                })
                .verify();
    }

    @Test
    void shouldThrowServiceUnavailableWhenConnectionRefused() {
        ExchangeFunction exchangeFunction = request -> Mono.error(new ConnectException("Connection refused"));
        RemoteAuthenticationProvider provider = providerWithExchange(exchangeFunction);

        StepVerifier.create(provider.authenticate(exchange("Bearer test-token", "corr-123")))
                .expectErrorSatisfies(error -> {
                    assertInstanceOf(ResponseStatusException.class, error);
                    assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                            ((ResponseStatusException) error).getStatusCode());
                })
                .verify();
    }

    @Test
    void shouldThrowServiceUnavailableWhenMalformedJsonResponse() {
        String invalidJson = "{invalid";
        RemoteAuthenticationProvider provider = providerWithJsonResponse(invalidJson, HttpStatus.OK);

        StepVerifier.create(provider.authenticate(exchange("Bearer test-token", "corr-123")))
                .expectErrorSatisfies(error -> {
                    assertInstanceOf(ResponseStatusException.class, error);
                    assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                            ((ResponseStatusException) error).getStatusCode());
                })
                .verify();
    }

    @Test
    void shouldPropagateCorrelationId() {
        String json = """
                {
                    "authenticated": true,
                    "subject": "user-123"
                }
                """;

        RemoteAuthenticationProvider provider = providerWithJsonResponse(json, HttpStatus.OK);

        StepVerifier.create(provider.authenticate(exchange("Bearer test-token", "my-custom-correlation-id")))
                .assertNext(result -> assertTrue(result.authenticated()))
                .verifyComplete();
    }

    private static RemoteAuthenticationProvider providerWithJsonResponse(String body, HttpStatus status) {
        byte[] raw = body.getBytes(StandardCharsets.UTF_8);
        ClientResponse response = ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(Flux.just(BUFFER_FACTORY.wrap(raw)))
                .build();
        return providerWithResponse(response);
    }

    private static RemoteAuthenticationProvider providerWithResponse(ClientResponse response) {
        return providerWithExchange(request -> Mono.just(response));
    }

    private static RemoteAuthenticationProvider providerWithExchange(ExchangeFunction exchangeFunction) {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build();
        return new RemoteAuthenticationProvider(webClient);
    }

    private static ServerWebExchange exchange(String authorization, String correlationId) {
        var builder = MockServerHttpRequest.get("/test");
        if (authorization != null) {
            builder.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        if (correlationId != null) {
            builder.header(HeaderConstants.X_CORRELATION_ID, correlationId);
        }
        return MockServerWebExchange.from(builder.build());
    }
}
