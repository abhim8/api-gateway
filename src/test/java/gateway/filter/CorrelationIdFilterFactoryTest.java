package gateway.filter;

import gateway.common.util.HeaderConstants;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterFactoryTest {

    private final CorrelationIdFilterFactory filter = new CorrelationIdFilterFactory();

    @Test
    void shouldGenerateIdWhenNotPresent() {
        ServerWebExchange exchange = createExchange(null);
        CapturingChain chain = new CapturingChain();

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        String responseId = exchange.getResponse().getHeaders()
                .getFirst(HeaderConstants.X_CORRELATION_ID);
        assertThat(responseId).isNotNull();
        assertThat(isValidUuid(responseId)).isTrue();
    }

    @Test
    void shouldForwardExistingId() {
        String existingId = "existing-correlation-id-123";
        ServerWebExchange exchange = createExchange(existingId);
        CapturingChain chain = new CapturingChain();

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        String responseId = exchange.getResponse().getHeaders()
                .getFirst(HeaderConstants.X_CORRELATION_ID);
        assertThat(responseId).isEqualTo(existingId);
    }

    @Test
    void shouldAddIdToResponseHeaders() {
        ServerWebExchange exchange = createExchange(null);
        CapturingChain chain = new CapturingChain();

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getHeaders()
                .get(HeaderConstants.X_CORRELATION_ID))
                .isNotNull()
                .isNotEmpty();
    }

    @Test
    void shouldAddIdToRequestHeadersForUpstream() {
        ServerWebExchange exchange = createExchange(null);
        CapturingChain chain = new CapturingChain();

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        String requestId = chain.getExchangedExchange().getRequest().getHeaders()
                .getFirst(HeaderConstants.X_CORRELATION_ID);
        assertThat(requestId).isNotNull();
        assertThat(isValidUuid(requestId)).isTrue();
    }

    @Test
    void shouldSetExchangeAttribute() {
        ServerWebExchange exchange = createExchange(null);
        CapturingChain chain = new CapturingChain();

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        String attribute = exchange.getAttribute(CorrelationIdFilterFactory.CORRELATION_ID_ATTRIBUTE);
        assertThat(attribute).isNotNull();
        assertThat(isValidUuid(attribute)).isTrue();
    }

    @Test
    void shouldSetReactorContext() {
        ServerWebExchange exchange = createExchange(null);
        CapturingChain chain = new CapturingChain();

        Mono<Void> result = filter.filter(exchange, chain);

        StepVerifier.create(result)
                .expectAccessibleContext()
                .hasKey(CorrelationIdFilterFactory.CORRELATION_ID_ATTRIBUTE)
                .then()
                .verifyComplete();
    }

    private static ServerWebExchange createExchange(String correlationId) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get("/test");
        if (correlationId != null) {
            builder.header(HeaderConstants.X_CORRELATION_ID, correlationId);
        }
        return MockServerWebExchange.from(builder.build());
    }

    private static boolean isValidUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static class CapturingChain implements GatewayFilterChain {

        private ServerWebExchange exchangedExchange;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            this.exchangedExchange = exchange;
            return Mono.empty();
        }

        ServerWebExchange getExchangedExchange() {
            return exchangedExchange;
        }
    }
}
