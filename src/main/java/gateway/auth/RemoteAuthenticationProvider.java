package gateway.auth;

import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public class RemoteAuthenticationProvider implements AuthenticationProvider {

    @Override
    public Mono<AuthenticationResult> authenticate(ServerWebExchange exchange) {
        return Mono.error(new UnsupportedOperationException("Auth Service integration not implemented yet."));
    }
}
