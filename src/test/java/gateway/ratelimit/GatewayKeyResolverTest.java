package gateway.ratelimit;

import gateway.auth.dto.AuthenticationResult;
import gateway.common.util.HeaderConstants;
import gateway.filter.AuthenticationGlobalFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;

class GatewayKeyResolverTest {

    private final GatewayKeyResolver resolver = new GatewayKeyResolver();

    @Test
    void shouldResolveFromApiKeyHeader() {
        ServerWebExchange exchange = exchangeWithHeader(HeaderConstants.X_API_KEY, "key-123");

        StepVerifier.create(resolver.resolve(exchange))
                .expectNext("key-123")
                .verifyComplete();
    }

    @Test
    void shouldResolveFromAuthenticatedSubject() {
        ServerWebExchange exchange = exchangeWithoutHeaders();
        exchange.getAttributes().put(
                AuthenticationGlobalFilter.AUTH_RESULT_ATTRIBUTE,
                AuthenticationResult.authenticated("alice"));

        StepVerifier.create(resolver.resolve(exchange))
                .expectNext("alice")
                .verifyComplete();
    }

    @Test
    void shouldResolveFromXForwardedFor() {
        ServerWebExchange exchange =
                exchangeWithHeader(HeaderConstants.X_FORWARDED_FOR, "203.0.113.42, 10.0.0.1");

        StepVerifier.create(resolver.resolve(exchange))
                .expectNext("203.0.113.42")
                .verifyComplete();
    }

    @Test
    void shouldResolveFromRemoteIp() {
        ServerWebExchange exchange = exchangeWithoutHeaders();
        MockServerHttpRequest request = MockServerHttpRequest.get("/test")
                .remoteAddress(new InetSocketAddress("198.51.100.7", 54321))
                .build();
        exchange = MockServerWebExchange.from(request);

        StepVerifier.create(resolver.resolve(exchange))
                .expectNext("198.51.100.7")
                .verifyComplete();
    }

    @Test
    void shouldFallbackToAnonymous() {
        ServerWebExchange exchange = exchangeWithoutHeaders();

        StepVerifier.create(resolver.resolve(exchange))
                .expectNext("anonymous")
                .verifyComplete();
    }

    @Test
    void shouldPreferApiKeyOverSubject() {
        ServerWebExchange exchange = exchangeWithHeader(HeaderConstants.X_API_KEY, "key-456");
        exchange.getAttributes().put(
                AuthenticationGlobalFilter.AUTH_RESULT_ATTRIBUTE,
                AuthenticationResult.authenticated("bob"));

        StepVerifier.create(resolver.resolve(exchange))
                .expectNext("key-456")
                .verifyComplete();
    }

    @Test
    void shouldPreferSubjectOverXForwardedFor() {
        ServerWebExchange exchange =
                exchangeWithHeader(HeaderConstants.X_FORWARDED_FOR, "203.0.113.42");
        exchange.getAttributes().put(
                AuthenticationGlobalFilter.AUTH_RESULT_ATTRIBUTE,
                AuthenticationResult.authenticated("carol"));

        StepVerifier.create(resolver.resolve(exchange))
                .expectNext("carol")
                .verifyComplete();
    }

    @Test
    void shouldPreferXForwardedForOverRemoteIp() {
        ServerWebExchange exchange =
                exchangeWithHeader(HeaderConstants.X_FORWARDED_FOR, "203.0.113.99");
        MockServerHttpRequest request = MockServerHttpRequest.get("/test")
                .header(HeaderConstants.X_FORWARDED_FOR, "203.0.113.99")
                .remoteAddress(new InetSocketAddress("10.0.0.1", 12345))
                .build();
        exchange = MockServerWebExchange.from(request);

        StepVerifier.create(resolver.resolve(exchange))
                .expectNext("203.0.113.99")
                .verifyComplete();
    }

    @Test
    void shouldNotUseSubjectWhenNotAuthenticated() {
        ServerWebExchange exchange = exchangeWithoutHeaders();
        exchange.getAttributes().put(
                AuthenticationGlobalFilter.AUTH_RESULT_ATTRIBUTE,
                AuthenticationResult.unauthenticated());

        StepVerifier.create(resolver.resolve(exchange))
                .expectNext(GatewayKeyResolver.ANONYMOUS)
                .verifyComplete();
    }

    private static ServerWebExchange exchangeWithHeader(String name, String value) {
        MockServerHttpRequest request = MockServerHttpRequest.get("/test")
                .header(name, value)
                .build();
        return MockServerWebExchange.from(request);
    }

    private static ServerWebExchange exchangeWithoutHeaders() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/test").build());
    }
}
