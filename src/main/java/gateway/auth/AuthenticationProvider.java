package gateway.auth;

import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@FunctionalInterface
public interface AuthenticationProvider {

    Mono<AuthenticationResult> authenticate(ServerWebExchange exchange);
}
