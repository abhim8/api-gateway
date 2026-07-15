package gateway.observability;

import gateway.filter.CorrelationIdGlobalFilter;
import io.opentelemetry.api.trace.Span;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.StringMapMessage;
import org.jspecify.annotations.NonNull;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * Measures total request processing time for every request passing through the gateway.
 *
 * <p>Instances are created exclusively by {@link ObservabilityConfiguration}, which uses
 * {@code @ConditionalOnProperty} to ensure this filter is never registered when
 * {@code gateway.logging.request-timing.enabled=false}.
 *
 * <p><b>Order rationale:</b> This filter runs at order <b>-110</b>, which is earlier (more
 * negative) than all other custom filters:
 * <ul>
 *   <li>{@link gateway.filter.CorrelationIdGlobalFilter} runs at -100</li>
 *   <li>{@link gateway.filter.AuthenticationGlobalFilter} runs at -75</li>
 * </ul>
 * Being the outermost filter ensures the recorded duration includes correlation ID resolution,
 * authentication, route matching, all built-in filters (retry, circuit breaker), and the
 * actual upstream proxy call. The correlation ID and trace ID are read from exchange
 * attributes and {@link Span#current()} rather than MDC, because MDC is owned by the
 * inner CorrelationIdGlobalFilter and is cleared before this filter's doFinally runs.
 */
public class RequestTimingGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LogManager.getLogger(RequestTimingGlobalFilter.class);

    static final String UNKNOWN = "unknown";
    static final String EVENT = "request-completed";

    private final long slowRequestThresholdMs;

    public RequestTimingGlobalFilter(long slowRequestThresholdMs) {
        this.slowRequestThresholdMs = slowRequestThresholdMs;
    }

    @Override
    public @NonNull Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull GatewayFilterChain chain) {
        long startTime = System.nanoTime();

        return chain.filter(exchange)
                .doFinally(signalType -> logTiming(exchange, startTime));
    }

    @Override
    public int getOrder() {
        return -110;
    }

    private void logTiming(ServerWebExchange exchange, long startTimeNanos) {
        try {
            long durationNanos = System.nanoTime() - startTimeNanos;
            long durationMs = durationNanos / 1_000_000;

            StringMapMessage msg = new StringMapMessage()
                    .with("event", EVENT)
                    .with("method", exchange.getRequest().getMethod().name())
                    .with("path", exchange.getRequest().getURI().getPath())
                    .with("status", String.valueOf(resolveStatus(exchange)))
                    .with("durationMs", String.valueOf(durationMs))
                    .with("routeId", resolveRouteId(exchange))
                    .with("remoteIp", resolveRemoteIp(exchange));

            String correlationId = resolveCorrelationId(exchange);
            if (!UNKNOWN.equals(correlationId)) {
                msg.with("correlationId", correlationId);
            }

            String traceId = resolveTraceId();
            if (!UNKNOWN.equals(traceId)) {
                msg.with("traceId", traceId);
            }

            if (durationMs > slowRequestThresholdMs) {
                log.warn(msg);
            } else {
                log.info(msg);
            }
        } catch (Exception e) {
            log.error("Failed to log request timing", e);
        }
    }

    private static int resolveStatus(ServerWebExchange exchange) {
        if (exchange.getResponse().getStatusCode() != null) {
            return exchange.getResponse().getStatusCode().value();
        }
        return 0;
    }

    private static String resolveCorrelationId(ServerWebExchange exchange) {
        String attr = exchange.getAttribute(CorrelationIdGlobalFilter.CORRELATION_ID_ATTRIBUTE);
        if (attr != null && !attr.isBlank()) {
            return attr;
        }
        return UNKNOWN;
    }

    private static String resolveTraceId() {
        Span span = Span.current();
        if (span.getSpanContext().isValid()) {
            return span.getSpanContext().getTraceId();
        }
        return UNKNOWN;
    }

    private static String resolveRouteId(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route != null) {
            return route.getId();
        }
        return UNKNOWN;
    }

    private static String resolveRemoteIp(ServerWebExchange exchange) {
        InetSocketAddress address = exchange.getRequest().getRemoteAddress();
        if (address != null && address.getAddress() != null) {
            return address.getAddress().getHostAddress();
        }
        return UNKNOWN;
    }
}
