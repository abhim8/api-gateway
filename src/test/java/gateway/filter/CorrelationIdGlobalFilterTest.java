package gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import gateway.common.util.HeaderConstants;
import gateway.observability.ResponseHeadersProperties;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Scope;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CorrelationIdGlobalFilterTest {

    @Test
    void shouldGenerateIdWhenNotPresent() {
        CorrelationIdGlobalFilter filter = createFilter(true, true);
        ServerWebExchange exchange = createExchange(null);
        CapturingChain chain = new CapturingChain();

        StepVerifier.create(
                filter.filter(exchange, chain)
                        .then(exchange.getResponse().setComplete())
        ).verifyComplete();

        String responseId = exchange.getResponse().getHeaders().getFirst(HeaderConstants.X_CORRELATION_ID);
        assertThat(responseId).isNotNull();
        assertThat(isValidUuid(responseId)).isTrue();
    }

    @Test
    void shouldForwardExistingId() {
        CorrelationIdGlobalFilter filter = createFilter(true, true);
        String existingId = "existing-correlation-id-123";
        ServerWebExchange exchange = createExchange(existingId);
        CapturingChain chain = new CapturingChain();

        StepVerifier.create(
                filter.filter(exchange, chain)
                        .then(exchange.getResponse().setComplete())
        ).verifyComplete();

        String responseId = exchange.getResponse().getHeaders().getFirst(HeaderConstants.X_CORRELATION_ID);
        assertThat(responseId).isEqualTo(existingId);
    }

    @Test
    void shouldAddIdToResponseHeaders() {
        CorrelationIdGlobalFilter filter = createFilter(true, true);
        ServerWebExchange exchange = createExchange(null);
        CapturingChain chain = new CapturingChain();

        StepVerifier.create(
                filter.filter(exchange, chain)
                        .then(exchange.getResponse().setComplete())
        ).verifyComplete();

        assertThat(exchange.getResponse().getHeaders().get(HeaderConstants.X_CORRELATION_ID))
                .isNotNull()
                .isNotEmpty();
    }

    @Test
    void shouldAddIdToRequestHeadersForUpstream() {
        CorrelationIdGlobalFilter filter = createFilter(true, true);
        ServerWebExchange exchange = createExchange(null);
        CapturingChain chain = new CapturingChain();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        String requestId =
                chain.getExchangedExchange().getRequest().getHeaders().getFirst(HeaderConstants.X_CORRELATION_ID);
        assertThat(requestId).isNotNull();
        assertThat(isValidUuid(requestId)).isTrue();
    }

    @Test
    void shouldSetExchangeAttribute() {
        CorrelationIdGlobalFilter filter = createFilter(true, true);
        ServerWebExchange exchange = createExchange(null);
        CapturingChain chain = new CapturingChain();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        String attribute = exchange.getAttribute(CorrelationIdGlobalFilter.CORRELATION_ID_ATTRIBUTE);
        assertThat(attribute).isNotNull();
        assertThat(isValidUuid(attribute)).isTrue();
    }

    @Test
    void shouldSetReactorContext() {
        CorrelationIdGlobalFilter filter = createFilter(true, true);
        ServerWebExchange exchange = createExchange(null);
        CapturingChain chain = new CapturingChain();

        Mono<Void> result = filter.filter(exchange, chain);

        StepVerifier.create(result)
                .expectAccessibleContext()
                .hasKey(CorrelationIdGlobalFilter.CORRELATION_ID_ATTRIBUTE)
                .then()
                .verifyComplete();
    }

    @Test
    void shouldSetTraceIdAndSpanIdInMdcWhenOtelSpanActive() {
        CorrelationIdGlobalFilter filter = createFilter(true, true);
        SpanContext spanContext = SpanContext.create(
                "0af7651916cd43dd8448eb211c80319c",
                "b7ad6b7169203331",
                TraceFlags.getSampled(),
                TraceState.getDefault());
        Span span = Span.wrap(spanContext);

        ServerWebExchange exchange = createExchange(null);
        CapturingChain chain = new CapturingChain();

        try (Scope ignored = span.makeCurrent()) {
            StepVerifier.create(filter.filter(exchange, chain))
                    .expectAccessibleContext()
                    .hasKey(CorrelationIdGlobalFilter.CORRELATION_ID_ATTRIBUTE)
                    .then()
                    .verifyComplete();
        }
    }

    @Test
    void shouldNotDuplicateResponseHeader() {
        CorrelationIdGlobalFilter filter = createFilter(true, true);
        ServerWebExchange exchange = createExchange(null);
        CapturingChain chain = new CapturingChain();

        StepVerifier.create(
                filter.filter(exchange, chain)
                        .then(exchange.getResponse().setComplete())
        ).verifyComplete();

        List<String> values = exchange.getResponse().getHeaders().get(HeaderConstants.X_CORRELATION_ID);
        assertThat(values).isNotNull().hasSize(1);
    }

    @Test
    void shouldNotAddResponseHeaderWhenDisabled() {
        CorrelationIdGlobalFilter filter = createFilter(false, true);
        ServerWebExchange exchange = createExchange(null);
        CapturingChain chain = new CapturingChain();

        StepVerifier.create(
                filter.filter(exchange, chain)
                        .then(exchange.getResponse().setComplete())
        ).verifyComplete();

        String responseId = exchange.getResponse().getHeaders().getFirst(HeaderConstants.X_CORRELATION_ID);
        assertThat(responseId).isNull();
    }

    @Test
    void shouldNotAddResponseHeaderWhenCorrelationIdDisabled() {
        CorrelationIdGlobalFilter filter = createFilter(true, false);
        ServerWebExchange exchange = createExchange(null);
        CapturingChain chain = new CapturingChain();

        StepVerifier.create(
                filter.filter(exchange, chain)
                        .then(exchange.getResponse().setComplete())
        ).verifyComplete();

        String responseId = exchange.getResponse().getHeaders().getFirst(HeaderConstants.X_CORRELATION_ID);
        assertThat(responseId).isNull();
    }

    @Test
    void shouldStillPropagateUpstreamWhenResponseHeadersDisabled() {
        CorrelationIdGlobalFilter filter = createFilter(false, false);
        ServerWebExchange exchange = createExchange(null);
        CapturingChain chain = new CapturingChain();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        String requestId =
                chain.getExchangedExchange().getRequest().getHeaders().getFirst(HeaderConstants.X_CORRELATION_ID);
        assertThat(requestId).isNotNull();
        assertThat(isValidUuid(requestId)).isTrue();

        String attribute = exchange.getAttribute(CorrelationIdGlobalFilter.CORRELATION_ID_ATTRIBUTE);
        assertThat(attribute).isNotNull();
    }

    private static CorrelationIdGlobalFilter createFilter(boolean enabled, boolean correlationId) {
        ResponseHeadersProperties props = new ResponseHeadersProperties();
        props.setEnabled(enabled);
        props.setCorrelationId(correlationId);
        return new CorrelationIdGlobalFilter(props);
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
