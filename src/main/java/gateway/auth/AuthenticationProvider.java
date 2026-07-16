package gateway.auth;

import gateway.auth.dto.AuthenticationResult;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@FunctionalInterface
public interface AuthenticationProvider {

    Mono<AuthenticationResult> authenticate(ServerWebExchange exchange);
}
