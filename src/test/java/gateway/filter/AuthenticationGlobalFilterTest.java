package gateway.filter;

import gateway.auth.AuthenticationProvider;
import gateway.auth.AuthenticationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticationGlobalFilterTest {

    private CapturingChain chain;

    @BeforeEach
    void setUp() {
        chain = new CapturingChain();
    }

    @Test
    void shouldInvokeProviderForEveryRequest() {
        AuthenticationProvider provider = exchange -> Mono.just(AuthenticationResult.authenticated("user"));
        AuthenticationGlobalFilter filter = new AuthenticationGlobalFilter(provider);

        ServerWebExchange exchange = createExchange();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertTrue(chain.wasInvoked());
    }

    @Test
    void shouldProceedWhenAuthenticated() {
        AuthenticationProvider provider = exchange -> Mono.just(AuthenticationResult.authenticated("user"));
        AuthenticationGlobalFilter filter = new AuthenticationGlobalFilter(provider);

        ServerWebExchange exchange = createExchange();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertNull(exchange.getResponse().getStatusCode());
        assertNotNull(exchange.getAttribute(AuthenticationGlobalFilter.AUTH_RESULT_ATTRIBUTE));
    }

    @Test
    void shouldReturn401WhenUnauthenticated() {
        AuthenticationProvider provider = exchange -> Mono.just(AuthenticationResult.unauthenticated());
        AuthenticationGlobalFilter filter = new AuthenticationGlobalFilter(provider);

        ServerWebExchange exchange = createExchange();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertFalse(chain.wasInvoked());
    }

    @Test
    void shouldPropagateProviderException() {
        AuthenticationProvider provider =
                exchange -> Mono.error(new RuntimeException("auth failed"));
        AuthenticationGlobalFilter filter = new AuthenticationGlobalFilter(provider);

        ServerWebExchange exchange = createExchange();

        StepVerifier.create(filter.filter(exchange, chain))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldHaveOrderMinus75() {
        AuthenticationProvider provider = exchange -> Mono.just(AuthenticationResult.authenticated("user"));
        AuthenticationGlobalFilter filter = new AuthenticationGlobalFilter(provider);

        assertEquals(-75, filter.getOrder());
    }

    private static ServerWebExchange createExchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/test").build());
    }

    private static class CapturingChain implements GatewayFilterChain {

        private boolean invoked;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            invoked = true;
            return Mono.empty();
        }

        boolean wasInvoked() {
            return invoked;
        }
    }
}
