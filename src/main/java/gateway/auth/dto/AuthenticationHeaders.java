package gateway.auth.dto;

import lombok.Builder;

@Builder
public record AuthenticationHeaders(
    String authorization,
    String apiKey,
    String cookie,
    String userAgent,
    String xForwardedFor,
    String xForwardedHost,
    String xForwardedPort,
    String xForwardedProto,
    String xForwardedPrefix,
    String origin,
    String referer,
    String acceptLanguage,
    String host
) {
}
