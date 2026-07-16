package gateway.auth;

import gateway.auth.dto.AuthenticationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
public class MockAuthenticationProvider implements AuthenticationProvider {

    @Override
    public Mono<AuthenticationResult> authenticate(ServerWebExchange exchange) {
        log.debug("Mock provider invoked");
        log.info("Mock authentication successful - subject: mock-user");
        return Mono.just(AuthenticationResult.authenticated("mock-user"));
    }
}
