package gateway.auth;

import gateway.auth.dto.AuthenticationClaims;
import gateway.auth.dto.AuthenticationHeaders;
import gateway.auth.dto.AuthValidationRequest;
import gateway.auth.dto.AuthValidationResponse;
import gateway.auth.dto.AuthenticationResult;
import gateway.auth.properties.RemoteAuthenticationProperties;
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
import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class RemoteAuthenticationProvider implements AuthenticationProvider {

    static final String AUTH_VALIDATE_PATH = "/internal/v1/auth/validate";
    static final String GATEWAY_USER_AGENT = "api-gateway";

    private final WebClient webClient;
    private final RemoteAuthenticationProperties properties;

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
                .toEntity(AuthValidationResponse.class)
                .flatMap(responseEntity -> {
                    long elapsed = Duration.ofNanos(System.nanoTime() - start).toMillis();
                    log.info("Authentication latency: {}ms", elapsed);

                    AuthValidationResponse response = responseEntity.getBody();
                    if (response == null || !response.authenticated()) {
                        log.warn("Remote authentication failed - not authenticated");
                        return Mono.just(AuthenticationResult.unauthenticated());
                    }

                    AuthenticationClaims claims = response.claims();
                    String subject = claims != null ? claims.subject() : null;
                    log.debug("Remote authentication successful - subject: {}", subject);

                    AuthenticationResult result = toResult(response);

                    List<String> relayHeaders = properties.getRelayResponseHeaders();
                    if (relayHeaders != null) {
                        HttpHeaders authResponseHeaders = responseEntity.getHeaders();
                        for (String headerName : relayHeaders) {
                            List<String> values = authResponseHeaders.get(headerName);
                            if (values != null && !values.isEmpty()) {
                                log.debug("Relaying response header: {}", headerName);
                                values.forEach(value ->
                                        result.relayResponseHeaders().add(headerName, value));
                            }
                        }
                    }

                    return Mono.just(result);
                })
                .onErrorResume(WebClientResponseException.class, e -> {
                    long elapsed = Duration.ofNanos(System.nanoTime() - start).toMillis();
                    log.info("Authentication latency: {}ms", elapsed);
                    if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                        log.warn("Remote authentication failed - invalid credentials (401)");
                        return Mono.just(AuthenticationResult.unauthenticated());
                    }
                    log.error("Remote authentication service error: {} {}", e.getStatusCode(), e.getMessage());
                    return Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "Remote authentication service unavailable"));
                })
                .onErrorResume(throwable -> {
                    long elapsed = Duration.ofNanos(System.nanoTime() - start).toMillis();
                    log.info("Authentication latency: {}ms", elapsed);
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
        AuthenticationClaims claims = response.claims();
        return new AuthenticationResult(
                true,
                claims != null ? claims.subject() : null,
                claims != null && claims.roles() != null ? claims.roles() : List.of(),
                claims != null && claims.permissions() != null ? claims.permissions() : List.of(),
                claims,
                new HttpHeaders()
        );
    }
}
