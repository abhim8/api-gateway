package gateway.filter;

import gateway.auth.AuthenticationProvider;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class AuthenticationGlobalFilter implements GlobalFilter, Ordered {

    public static final String AUTH_RESULT_ATTRIBUTE = "authenticationResult";

    private final AuthenticationProvider authenticationProvider;

    public AuthenticationGlobalFilter(AuthenticationProvider authenticationProvider) {
        this.authenticationProvider = authenticationProvider;
    }

    @Override
    public @NonNull Mono<Void> filter(ServerWebExchange exchange, @NonNull GatewayFilterChain chain) {
        log.debug("Entering AuthenticationGlobalFilter");
        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getURI().getPath();
        String providerName = authenticationProvider.getClass().getSimpleName();

        log.debug("Incoming request: {} {}", method, path);
        log.debug("Authentication started; provider: {}", providerName);

        return authenticationProvider.authenticate(exchange)
                .flatMap(result -> {
                    if (!result.authenticated()) {
                        log.warn("Authentication failed for {} {}; returning 401", method, path);
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        return exchange.getResponse().setComplete();
                    }
                    log.info("Authentication successful - subject: {}, roles: {}",
                            result.subject(),
                            result.roles().isEmpty() ? "none" : result.roles());
                    exchange.getAttributes().put(AUTH_RESULT_ATTRIBUTE, result);
                    return chain.filter(exchange);
                })
                .doOnError(e -> log.error("Unexpected exception from AuthenticationProvider ({}):", providerName, e))
                .doFinally(_ -> log.debug("Leaving AuthenticationGlobalFilter"));
    }

    @Override
    public int getOrder() {
        return -75;
    }
}
