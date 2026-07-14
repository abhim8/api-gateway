package gateway.filter;

import gateway.common.util.HeaderConstants;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class CorrelationIdFilterFactory implements GlobalFilter, Ordered {

    static final String CORRELATION_ID_ATTRIBUTE = "correlationId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = resolveCorrelationId(exchange);
        exchange.getAttributes().put(CORRELATION_ID_ATTRIBUTE, correlationId);

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(exchange.getRequest().mutate()
                        .header(HeaderConstants.X_CORRELATION_ID, correlationId)
                        .build())
                .response(exchange.getResponse())
                .build();

        return chain.filter(mutatedExchange)
                .then(Mono.<Void>defer(() -> {
                    mutatedExchange.getResponse().getHeaders()
                            .add(HeaderConstants.X_CORRELATION_ID, correlationId);
                    return Mono.empty();
                }))
                .contextWrite(ctx -> ctx.put(CORRELATION_ID_ATTRIBUTE, correlationId));
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private static String resolveCorrelationId(ServerWebExchange exchange) {
        String existing = exchange.getRequest().getHeaders()
                .getFirst(HeaderConstants.X_CORRELATION_ID);
        if (existing != null && !existing.isBlank()) {
            return existing;
        }
        return UUID.randomUUID().toString();
    }
}
