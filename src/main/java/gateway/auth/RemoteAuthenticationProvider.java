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

    static final String AUTH_VALIDATE_PATH = "/internal/v1/auth/validate";
    static final String GATEWAY_USER_AGENT = "api-gateway";
    static final String CLAIMS_USERNAME = "username";
    static final String CLAIMS_EMAIL = "email";
    static final String CLAIMS_EXPIRES_AT = "expiresAt";
    static final String CLAIMS_TENANT_ID = "tenantId";

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
                    headers.set(HttpHeaders.USER_AGENT, GATEWAY_USER_AGENT);
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
        if (headers.authorization() != null) names.add(HttpHeaders.AUTHORIZATION);
        if (headers.apiKey() != null) names.add(HeaderConstants.X_API_KEY);
        if (headers.cookie() != null) names.add(HttpHeaders.COOKIE);
        if (headers.userAgent() != null) names.add(HttpHeaders.USER_AGENT);
        if (headers.xForwardedFor() != null) names.add(HeaderConstants.X_FORWARDED_FOR);
        if (headers.xForwardedHost() != null) names.add(HeaderConstants.X_FORWARDED_HOST);
        if (headers.xForwardedPort() != null) names.add(HeaderConstants.X_FORWARDED_PORT);
        if (headers.xForwardedProto() != null) names.add(HeaderConstants.X_FORWARDED_PROTO);
        if (headers.xForwardedPrefix() != null) names.add(HeaderConstants.X_FORWARDED_PREFIX);
        if (headers.origin() != null) names.add(HttpHeaders.ORIGIN);
        if (headers.referer() != null) names.add(HttpHeaders.REFERER);
        if (headers.acceptLanguage() != null) names.add(HttpHeaders.ACCEPT_LANGUAGE);
        if (headers.host() != null) names.add(HttpHeaders.HOST);
        return names;
    }

    private AuthenticationResult toResult(AuthValidationResponse response) {
        HashMap<String, Object> claims = new HashMap<>();
        if (response.username() != null) {
            claims.put(CLAIMS_USERNAME, response.username());
        }
        if (response.email() != null) {
            claims.put(CLAIMS_EMAIL, response.email());
        }
        if (response.expiresAt() != null) {
            claims.put(CLAIMS_EXPIRES_AT, response.expiresAt().toString());
        }
        if (response.metadata() != null && response.metadata().containsKey(CLAIMS_TENANT_ID)) {
            claims.put(CLAIMS_TENANT_ID, response.metadata().get(CLAIMS_TENANT_ID));
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
