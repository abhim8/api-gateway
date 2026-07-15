package gateway.filter;

import gateway.common.util.HeaderConstants;
import io.opentelemetry.api.trace.Span;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class CorrelationIdGlobalFilter implements GlobalFilter, Ordered {

    public static final String CORRELATION_ID_ATTRIBUTE = "correlationId";

    @Override
    public @NonNull Mono<Void> filter(@NonNull ServerWebExchange exchange, GatewayFilterChain chain) {
        log.debug("Entering CorrelationIdGlobalFilter");
        String correlationId = resolveCorrelationId(exchange);
        exchange.getAttributes().put(CORRELATION_ID_ATTRIBUTE, correlationId);

        String traceId = resolveTraceId();
        String spanId = resolveSpanId();

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(exchange.getRequest()
                        .mutate()
                        .header(HeaderConstants.X_CORRELATION_ID, correlationId)
                        .build())
                .response(exchange.getResponse())
                .build();

        mutatedExchange.getResponse().beforeCommit(() -> {
            mutatedExchange.getResponse().getHeaders().add(HeaderConstants.X_CORRELATION_ID, correlationId);
            return Mono.empty();
        });

        return chain.filter(mutatedExchange)
                .contextWrite(ctx -> {
                    MDC.put(CORRELATION_ID_ATTRIBUTE, correlationId);
                    if (traceId != null) {
                        MDC.put("traceId", traceId);
                    }
                    if (spanId != null) {
                        MDC.put("spanId", spanId);
                    }
                    return ctx.put(CORRELATION_ID_ATTRIBUTE, correlationId);
                })
                .doFinally(signalType -> {
                    MDC.clear();
                    log.debug("Leaving CorrelationIdGlobalFilter");
                });
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private static String resolveCorrelationId(ServerWebExchange exchange) {
        String existing = exchange.getRequest().getHeaders().getFirst(HeaderConstants.X_CORRELATION_ID);
        if (existing != null && !existing.isBlank()) {
            return existing;
        }
        return UUID.randomUUID().toString();
    }

    private static String resolveTraceId() {
        Span span = Span.current();
        if (span.getSpanContext().isValid()) {
            return span.getSpanContext().getTraceId();
        }
        return null;
    }

    private static String resolveSpanId() {
        Span span = Span.current();
        if (span.getSpanContext().isValid()) {
            return span.getSpanContext().getSpanId();
        }
        return null;
    }
}
