package gateway.auth;

import gateway.auth.dto.AuthenticationHeaders;
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
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class RemoteAuthenticationProvider implements AuthenticationProvider {

    private static final String AUTH_VALIDATE_PATH = "/internal/v1/auth/validate";

    private final WebClient webClient;

    @Override
    public Mono<AuthenticationResult> authenticate(ServerWebExchange exchange) {
        AuthenticationHeaders authHeaders = buildAuthenticationHeaders(exchange);

        String correlationId = exchange.getRequest().getHeaders()
                .getFirst(HeaderConstants.X_CORRELATION_ID);

        log.debug("Remote authentication request forwarding headers: {}",
                String.join(", ", extractForwardedHeaderNames(authHeaders)));

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
                .bodyValue(new AuthValidationRequest(
                        correlationId != null ? correlationId : UUID.randomUUID().toString(),
                        authHeaders))
                .retrieve()
                .bodyToMono(AuthValidationResponse.class)
                .map(response -> {
                    log.info("Authentication latency: {}ms", Duration.ofNanos(System.nanoTime() - start).toMillis());
                    if (!response.authenticated()) {
                        log.warn("Remote authentication failed - not authenticated");
                        return AuthenticationResult.unauthenticated();
                    }
                    log.debug("Remote authentication successful - subject: {}", response.subject());
                    return toResult(response);
                })
                .onErrorResume(WebClientResponseException.class, e -> {
                    log.info("Authentication latency: {}ms", Duration.ofNanos(System.nanoTime() - start).toMillis());
                    if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                        log.warn("Remote authentication failed - invalid credentials (401)");
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

    static AuthenticationHeaders buildAuthenticationHeaders(ServerWebExchange exchange) {
        HttpHeaders httpHeaders = exchange.getRequest().getHeaders();
        return AuthenticationHeaders.builder()
                .authorization(httpHeaders.getFirst(HttpHeaders.AUTHORIZATION))
                .apiKey(httpHeaders.getFirst(HeaderConstants.X_API_KEY))
                .cookie(httpHeaders.getFirst(HttpHeaders.COOKIE))
                .userAgent(httpHeaders.getFirst(HttpHeaders.USER_AGENT))
                .xForwardedFor(httpHeaders.getFirst(HeaderConstants.X_FORWARDED_FOR))
                .xForwardedHost(httpHeaders.getFirst(HeaderConstants.X_FORWARDED_HOST))
                .xForwardedPort(httpHeaders.getFirst(HeaderConstants.X_FORWARDED_PORT))
                .xForwardedProto(httpHeaders.getFirst(HeaderConstants.X_FORWARDED_PROTO))
                .xForwardedPrefix(httpHeaders.getFirst(HeaderConstants.X_FORWARDED_PREFIX))
                .origin(httpHeaders.getFirst(HttpHeaders.ORIGIN))
                .referer(httpHeaders.getFirst(HttpHeaders.REFERER))
                .acceptLanguage(httpHeaders.getFirst(HttpHeaders.ACCEPT_LANGUAGE))
                .host(httpHeaders.getFirst(HttpHeaders.HOST))
                .build();
    }

    private static List<String> extractForwardedHeaderNames(AuthenticationHeaders headers) {
        List<String> names = new java.util.ArrayList<>();
        if (headers.authorization() != null) names.add("Authorization");
        if (headers.apiKey() != null) names.add("X-API-Key");
        if (headers.cookie() != null) names.add("Cookie");
        if (headers.userAgent() != null) names.add("User-Agent");
        if (headers.xForwardedFor() != null) names.add("X-Forwarded-For");
        if (headers.xForwardedHost() != null) names.add("X-Forwarded-Host");
        if (headers.xForwardedPort() != null) names.add("X-Forwarded-Port");
        if (headers.xForwardedProto() != null) names.add("X-Forwarded-Proto");
        if (headers.xForwardedPrefix() != null) names.add("X-Forwarded-Prefix");
        if (headers.origin() != null) names.add("Origin");
        if (headers.referer() != null) names.add("Referer");
        if (headers.acceptLanguage() != null) names.add("Accept-Language");
        if (headers.host() != null) names.add("Host");
        return names;
    }

    private AuthenticationResult toResult(AuthValidationResponse response) {
        HashMap<String, Object> claims = new HashMap<>();
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
