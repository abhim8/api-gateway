package gateway.auth;

import gateway.auth.dto.AuthenticationHeaders;
import gateway.auth.properties.RemoteAuthenticationProperties;
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
                    "claims": {
                        "subject": "user-123",
                        "username": "abhilash",
                        "email": "abhilash@example.com",
                        "roles": ["USER", "ADMIN"],
                        "permissions": ["template.read", "template.write"],
                        "tenantId": "tenant-001"
                    }
                }
                """;

        RemoteAuthenticationProvider provider = providerWithJsonResponse(json, HttpStatus.OK);

        StepVerifier.create(provider.authenticate(exchange("Bearer test-token", "corr-123")))
                .assertNext(result -> {
                    assertTrue(result.authenticated());
                    assertEquals("user-123", result.subject());
                    assertEquals(List.of("USER", "ADMIN"), result.roles());
                    assertEquals(List.of("template.read", "template.write"), result.permissions());
                    assertEquals("abhilash", result.claims().username());
                    assertEquals("abhilash@example.com", result.claims().email());
                    assertEquals("tenant-001", result.claims().tenantId());
                    assertEquals("user-123", result.claims().subject());
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnAuthenticatedWithMinimalFields() {
        String json = """
                {
                    "authenticated": true,
                    "claims": {
                        "subject": "user-456"
                    }
                }
                """;

        RemoteAuthenticationProvider provider = providerWithJsonResponse(json, HttpStatus.OK);

        StepVerifier.create(provider.authenticate(exchange("Bearer test-token", "corr-456")))
                .assertNext(result -> {
                    assertTrue(result.authenticated());
                    assertEquals("user-456", result.subject());
                    assertTrue(result.roles().isEmpty());
                    assertTrue(result.permissions().isEmpty());
                    assertNotNull(result.claims());
                    assertEquals("user-456", result.claims().subject());
                    assertNull(result.claims().username());
                    assertNull(result.claims().email());
                    assertNull(result.claims().tenantId());
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
        RemoteAuthenticationProvider provider = providerWithJsonResponse(
                "{\"authenticated\":false}", HttpStatus.OK);

        StepVerifier.create(provider.authenticate(exchange(null, "corr-123")))
                .assertNext(result -> assertFalse(result.authenticated()))
                .verifyComplete();
    }

    @Test
    void shouldReturnUnauthenticatedWhenInvalidBearerFormat() {
        RemoteAuthenticationProvider provider = providerWithJsonResponse(
                "{\"authenticated\":false}", HttpStatus.OK);

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
                    "claims": {
                        "subject": "user-123"
                    }
                }
                """;

        RemoteAuthenticationProvider provider = providerWithJsonResponse(json, HttpStatus.OK);

        StepVerifier.create(provider.authenticate(exchange("Bearer test-token", "my-custom-correlation-id")))
                .assertNext(result -> assertTrue(result.authenticated()))
                .verifyComplete();
    }

    @Test
    void shouldForwardSupportedHeaders() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-123")
                        .header(HeaderConstants.X_API_KEY, "ak-456")
                        .header(HttpHeaders.COOKIE, "session=s789")
                        .header(HttpHeaders.USER_AGENT, "TestAgent/1.0")
                        .header("X-Forwarded-For", "10.0.0.1")
                        .header("X-Forwarded-Host", "gateway.example.com")
                        .header("X-Forwarded-Port", "443")
                        .header("X-Forwarded-Proto", "https")
                        .header("X-Forwarded-Prefix", "/api")
                        .header(HttpHeaders.ORIGIN, "https://origin.example.com")
                        .header(HttpHeaders.REFERER, "https://referer.example.com/page")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .header(HttpHeaders.HOST, "gateway.example.com")
                        .build());

        AuthenticationHeaders headers = RemoteAuthenticationProvider.buildAuthenticationHeaders(exchange);

        assertEquals("Bearer token-123", headers.authorization());
        assertEquals("ak-456", headers.apiKey());
        assertEquals("session=s789", headers.cookie());
        assertEquals("TestAgent/1.0", headers.userAgent());
        assertEquals("10.0.0.1", headers.xForwardedFor());
        assertEquals("gateway.example.com", headers.xForwardedHost());
        assertEquals("443", headers.xForwardedPort());
        assertEquals("https", headers.xForwardedProto());
        assertEquals("/api", headers.xForwardedPrefix());
        assertEquals("https://origin.example.com", headers.origin());
        assertEquals("https://referer.example.com/page", headers.referer());
        assertEquals("en-US", headers.acceptLanguage());
        assertEquals("gateway.example.com", headers.host());
    }

    @Test
    void shouldNotIncludeMissingHeaders() {
        ServerWebExchange exchange = exchange("Bearer token", "corr-123");

        AuthenticationHeaders headers = RemoteAuthenticationProvider.buildAuthenticationHeaders(exchange);

        assertNull(headers.apiKey());
        assertNull(headers.cookie());
        assertNull(headers.userAgent());
        assertNull(headers.xForwardedFor());
        assertNull(headers.xForwardedHost());
        assertNull(headers.xForwardedPort());
        assertNull(headers.xForwardedProto());
        assertNull(headers.xForwardedPrefix());
        assertNull(headers.origin());
        assertNull(headers.referer());
        assertNull(headers.acceptLanguage());
        assertNull(headers.host());
    }

    @Test
    void shouldForwardAuthorizationUnchanged() {
        ServerWebExchange exchange = exchange("my-custom-auth-value", "corr-123");

        AuthenticationHeaders headers = RemoteAuthenticationProvider.buildAuthenticationHeaders(exchange);

        assertEquals("my-custom-auth-value", headers.authorization());
    }

    @Test
    void shouldForwardBearerAuthorizationUnchanged() {
        ServerWebExchange exchange = exchange("Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyLTEyMyJ9.x", "corr-123");

        AuthenticationHeaders headers = RemoteAuthenticationProvider.buildAuthenticationHeaders(exchange);

        assertEquals("Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyLTEyMyJ9.x", headers.authorization());
    }

    @Test
    void shouldForwardBasicAuthorizationUnchanged() {
        ServerWebExchange exchange = exchange("Basic dXNlcjpwYXNz", "corr-123");

        AuthenticationHeaders headers = RemoteAuthenticationProvider.buildAuthenticationHeaders(exchange);

        assertEquals("Basic dXNlcjpwYXNz", headers.authorization());
    }

    @Test
    void shouldForwardCookieUnchanged() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .header(HttpHeaders.COOKIE, "session=abc123; theme=dark")
                        .build());

        AuthenticationHeaders headers = RemoteAuthenticationProvider.buildAuthenticationHeaders(exchange);

        assertEquals("session=abc123; theme=dark", headers.cookie());
    }

    @Test
    void shouldForwardApiKeyUnchanged() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .header(HeaderConstants.X_API_KEY, "custom-api-key-value")
                        .build());

        AuthenticationHeaders headers = RemoteAuthenticationProvider.buildAuthenticationHeaders(exchange);

        assertEquals("custom-api-key-value", headers.apiKey());
    }

    @Test
    void shouldIgnoreUnsupportedHeaders() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .header("X-Custom-Header", "should-not-appear")
                        .header("X-Unsupported", "also-ignored")
                        .build());

        AuthenticationHeaders headers = RemoteAuthenticationProvider.buildAuthenticationHeaders(exchange);

        assertEquals("Bearer token", headers.authorization());
        assertNull(headers.userAgent());
        assertNull(headers.origin());
    }

    @Test
    void shouldForwardNullAuthorizationWhenMissing() {
        ServerWebExchange exchange = exchange(null, "corr-123");

        AuthenticationHeaders headers = RemoteAuthenticationProvider.buildAuthenticationHeaders(exchange);

        assertNull(headers.authorization());
    }

    @Test
    void shouldRelayAuthorizationResponseHeaderUnchanged() {
        String json = """
                {
                    "authenticated": true,
                    "claims": {
                        "subject": "user-123"
                    }
                }
                """;

        ClientResponse response = ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer relayed-token-789")
                .body(Flux.just(BUFFER_FACTORY.wrap(json.getBytes(StandardCharsets.UTF_8))))
                .build();

        RemoteAuthenticationProvider provider = providerWithResponse(response);

        StepVerifier.create(provider.authenticate(exchange("Bearer original-token", "corr-123")))
                .assertNext(result -> {
                    assertTrue(result.authenticated());
                    assertEquals("Bearer relayed-token-789",
                            result.relayResponseHeaders().getFirst(HttpHeaders.AUTHORIZATION));
                })
                .verifyComplete();
    }

    @Test
    void shouldRelaySetCookieResponseHeaderUnchanged() {
        String json = """
                {
                    "authenticated": true,
                    "claims": {
                        "subject": "user-123"
                    }
                }
                """;

        ClientResponse response = ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.SET_COOKIE, "session=relayed-session-id; Path=/")
                .body(Flux.just(BUFFER_FACTORY.wrap(json.getBytes(StandardCharsets.UTF_8))))
                .build();

        RemoteAuthenticationProvider provider = providerWithResponse(response);

        StepVerifier.create(provider.authenticate(exchange("Bearer token", "corr-123")))
                .assertNext(result -> {
                    assertTrue(result.authenticated());
                    assertEquals("session=relayed-session-id; Path=/",
                            result.relayResponseHeaders().getFirst(HttpHeaders.SET_COOKIE));
                })
                .verifyComplete();
    }

    @Test
    void shouldRelayMultipleSetCookieHeaders() {
        String json = """
                {
                    "authenticated": true,
                    "claims": {
                        "subject": "user-123"
                    }
                }
                """;

        ClientResponse response = ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.SET_COOKIE, "session=abc; Path=/")
                .header(HttpHeaders.SET_COOKIE, "theme=dark; Path=/")
                .body(Flux.just(BUFFER_FACTORY.wrap(json.getBytes(StandardCharsets.UTF_8))))
                .build();

        RemoteAuthenticationProvider provider = providerWithResponse(response);

        StepVerifier.create(provider.authenticate(exchange("Bearer token", "corr-123")))
                .assertNext(result -> {
                    assertTrue(result.authenticated());
                    List<String> cookies = result.relayResponseHeaders().get(HttpHeaders.SET_COOKIE);
                    assertNotNull(cookies);
                    assertEquals(2, cookies.size());
                    assertTrue(cookies.contains("session=abc; Path=/"));
                    assertTrue(cookies.contains("theme=dark; Path=/"));
                })
                .verifyComplete();
    }

    @Test
    void shouldIgnoreUnsupportedResponseHeaders() {
        String json = """
                {
                    "authenticated": true,
                    "claims": {
                        "subject": "user-123"
                    }
                }
                """;

        ClientResponse response = ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header("X-Custom-Header", "should-not-be-relayed")
                .header("X-Other", "also-ignored")
                .body(Flux.just(BUFFER_FACTORY.wrap(json.getBytes(StandardCharsets.UTF_8))))
                .build();

        RemoteAuthenticationProvider provider = providerWithResponse(response);

        StepVerifier.create(provider.authenticate(exchange("Bearer token", "corr-123")))
                .assertNext(result -> {
                    assertTrue(result.authenticated());
                    assertTrue(result.relayResponseHeaders().isEmpty());
                })
                .verifyComplete();
    }

    @Test
    void shouldNotRelayResponseHeadersWhenNoneMatch() {
        String json = """
                {
                    "authenticated": true,
                    "claims": {
                        "subject": "user-123"
                    }
                }
                """;

        RemoteAuthenticationProvider provider = providerWithJsonResponse(json, HttpStatus.OK);

        StepVerifier.create(provider.authenticate(exchange("Bearer token", "corr-123")))
                .assertNext(result -> {
                    assertTrue(result.authenticated());
                    assertTrue(result.relayResponseHeaders().isEmpty());
                })
                .verifyComplete();
    }

    @Test
    void shouldNotRelayResponseHeadersWhenUnauthenticated() {
        ClientResponse response = ClientResponse.create(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer should-not-be-relayed")
                .build();

        RemoteAuthenticationProvider provider = providerWithResponse(response);

        StepVerifier.create(provider.authenticate(exchange("Bearer invalid", "corr-123")))
                .assertNext(result -> {
                    assertFalse(result.authenticated());
                    assertNull(result.subject());
                    assertTrue(result.relayResponseHeaders().isEmpty());
                })
                .verifyComplete();
    }

    @Test
    void shouldPreserveExistingAuthenticationBehavior() {
        String json = """
                {
                    "authenticated": true,
                    "claims": {
                        "subject": "user-789",
                        "username": "existing-user",
                        "email": "existing@example.com",
                        "roles": ["USER"],
                        "permissions": ["read"],
                        "tenantId": "tenant-002"
                    }
                }
                """;

        ClientResponse response = ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer relayed")
                .header(HttpHeaders.SET_COOKIE, "session=existing-session; Path=/")
                .body(Flux.just(BUFFER_FACTORY.wrap(json.getBytes(StandardCharsets.UTF_8))))
                .build();

        RemoteAuthenticationProvider provider = providerWithResponse(response);

        StepVerifier.create(provider.authenticate(exchange("Bearer original", "corr-789")))
                .assertNext(result -> {
                    assertTrue(result.authenticated());
                    assertEquals("user-789", result.subject());
                    assertEquals(List.of("USER"), result.roles());
                    assertEquals(List.of("read"), result.permissions());
                    assertEquals("existing-user", result.claims().username());
                    assertEquals("existing@example.com", result.claims().email());
                    assertEquals("tenant-002", result.claims().tenantId());

                    assertEquals("Bearer relayed",
                            result.relayResponseHeaders().getFirst(HttpHeaders.AUTHORIZATION));
                    assertEquals("session=existing-session; Path=/",
                            result.relayResponseHeaders().getFirst(HttpHeaders.SET_COOKIE));
                })
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
        RemoteAuthenticationProperties properties = new RemoteAuthenticationProperties();
        properties.setRelayResponseHeaders(List.of("Authorization", "Set-Cookie"));
        return new RemoteAuthenticationProvider(webClient, properties);
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
