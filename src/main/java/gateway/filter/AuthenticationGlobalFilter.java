package gateway.filter;

import gateway.auth.AuthenticationProvider;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class AuthenticationGlobalFilter implements GlobalFilter, Ordered {

    public static final String AUTH_RESULT_ATTRIBUTE = "authenticationResult";

    private final AuthenticationProvider authenticationProvider;

    public AuthenticationGlobalFilter(AuthenticationProvider authenticationProvider) {
        this.authenticationProvider = authenticationProvider;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return authenticationProvider.authenticate(exchange)
                .flatMap(result -> {
                    if (!result.authenticated()) {
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        return exchange.getResponse().setComplete();
                    }
                    exchange.getAttributes().put(AUTH_RESULT_ATTRIBUTE, result);
                    return chain.filter(exchange);
                });
    }

    @Override
    public int getOrder() {
        return -75;
    }
}
