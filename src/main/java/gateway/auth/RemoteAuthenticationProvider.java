package gateway.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
public class RemoteAuthenticationProvider implements AuthenticationProvider {

    @Override
    public Mono<AuthenticationResult> authenticate(ServerWebExchange exchange) {
        log.debug("Remote provider invoked");
        log.info("Outbound authentication request initiated");
        return Mono.error(new UnsupportedOperationException(
                "Auth Platform integration is not implemented yet."));
    }
}
