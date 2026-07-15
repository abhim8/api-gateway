package gateway.auth;

import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public class MockAuthenticationProvider implements AuthenticationProvider {

    @Override
    public Mono<AuthenticationResult> authenticate(ServerWebExchange exchange) {
        return Mono.just(AuthenticationResult.authenticated("mock-user"));
    }
}
