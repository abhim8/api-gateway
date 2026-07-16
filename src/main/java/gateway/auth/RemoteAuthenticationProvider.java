package gateway.auth;

import gateway.auth.dto.AuthValidationRequest;
import gateway.auth.dto.AuthValidationResponse;
import gateway.auth.dto.AuthenticationResult;
import gateway.common.util.HeaderConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class RemoteAuthenticationProvider implements AuthenticationProvider {

    private static final String AUTH_VALIDATE_PATH = "/internal/v1/auth/validate";

    private final WebClient webClient;

    @Override
    public Mono<AuthenticationResult> authenticate(ServerWebExchange exchange) {
        log.info("Remote authentication started");

        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            log.warn("Remote authentication failed - no bearer token");
            return Mono.just(AuthenticationResult.unauthenticated());
        }

        String token = authorization.substring(7);
        String correlationId = exchange.getRequest().getHeaders()
                .getFirst(HeaderConstants.X_CORRELATION_ID);

        log.info("Remote authentication request");

        long start = System.nanoTime();

        return webClient.post()
                .uri(AUTH_VALIDATE_PATH)
                .headers(headers -> {
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.set(HttpHeaders.USER_AGENT, "api-gateway");
                    if (correlationId != null) {
                        headers.set(HeaderConstants.X_CORRELATION_ID, correlationId);
                    }
                })
                .bodyValue(new AuthValidationRequest(token, correlationId))
                .retrieve()
                .bodyToMono(AuthValidationResponse.class)
                .map(response -> {
                    log.info("Authentication latency: {}ms", Duration.ofNanos(System.nanoTime() - start).toMillis());
                    if (!response.authenticated()) {
                        log.warn("Remote authentication failed - inactive token");
                        return AuthenticationResult.unauthenticated();
                    }
                    log.info("Remote authentication successful - subject: {}", response.subject());
                    return toResult(response);
                })
                .onErrorResume(WebClientResponseException.class, e -> {
                    log.info("Authentication latency: {}ms", Duration.ofNanos(System.nanoTime() - start).toMillis());
                    if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                        log.warn("Remote authentication failed - invalid token (401)");
                        return Mono.just(AuthenticationResult.unauthenticated());
                    }
                    log.error("Remote authentication service error: {} {}", e.getStatusCode(), e.getMessage());
                    return Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "Remote authentication service unavailable"));
                })
                .onErrorResume(throwable -> {
                    log.info("Authentication latency: {}ms", Duration.ofNanos(System.nanoTime() - start).toMillis());
                    log.error("Remote authentication service unavailable: {}", throwable.getMessage());
                    return Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "Remote authentication service unavailable"));
                });
    }

    private AuthenticationResult toResult(AuthValidationResponse response) {
        Map<String, Object> claims = new HashMap<>();
        if (response.username() != null) {
            claims.put("username", response.username());
        }
        if (response.email() != null) {
            claims.put("email", response.email());
        }
        if (response.expiresAt() != null) {
            claims.put("expiresAt", response.expiresAt().toString());
        }
        if (response.metadata() != null && response.metadata().containsKey("tenantId")) {
            claims.put("tenantId", response.metadata().get("tenantId"));
        }

        return new AuthenticationResult(
                true,
                response.subject(),
                response.roles() != null ? response.roles() : List.of(),
                response.permissions() != null ? response.permissions() : List.of(),
                claims
        );
    }
}
