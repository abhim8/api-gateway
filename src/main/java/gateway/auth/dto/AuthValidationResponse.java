package gateway.auth.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AuthValidationResponse(
    boolean authenticated,
    String subject,
    String username,
    String email,
    List<String> roles,
    List<String> permissions,
    Instant expiresAt,
    Map<String, Object> metadata
) {
}
