package gateway.auth.dto;

import lombok.Builder;

import java.util.List;
import java.util.Map;

@Builder
public record AuthenticationClaims(
    String subject,
    String username,
    String email,
    List<String> roles,
    List<String> permissions,
    String tenantId,
    Map<String, Object> metadata
) {
}
