package gateway.ratelimit;

import gateway.auth.dto.AuthenticationResult;
import gateway.common.util.HeaderConstants;
import gateway.filter.AuthenticationGlobalFilter;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
public class GatewayKeyResolver implements KeyResolver {

    @Override
    public @NonNull Mono<String> resolve(@NonNull ServerWebExchange exchange) {
        String apiKey = exchange.getRequest().getHeaders().getFirst(HeaderConstants.X_API_KEY);
        if (apiKey != null && !apiKey.isBlank()) {
            log.debug("Rate limit key resolved from X-API-Key: {}", apiKey);
            return Mono.just(apiKey);
        }

        AuthenticationResult authResult =
                exchange.getAttribute(AuthenticationGlobalFilter.AUTH_RESULT_ATTRIBUTE);
        if (authResult != null && authResult.authenticated() && authResult.subject() != null
                && !authResult.subject().isBlank()) {
            log.debug("Rate limit key resolved from authenticated subject: {}", authResult.subject());
            return Mono.just(authResult.subject());
        }

        String xForwardedFor =
                exchange.getRequest().getHeaders().getFirst(HeaderConstants.X_FORWARDED_FOR);
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            String clientIp = xForwardedFor.split(",")[0].trim();
            log.debug("Rate limit key resolved from X-Forwarded-For: {}", clientIp);
            return Mono.just(clientIp);
        }

        if (exchange.getRequest().getRemoteAddress() != null) {
            String remoteIp = exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
            log.debug("Rate limit key resolved from remote IP: {}", remoteIp);
            return Mono.just(remoteIp);
        }

        log.debug("Rate limit key resolved as anonymous");
        return Mono.just("anonymous");
    }
}
