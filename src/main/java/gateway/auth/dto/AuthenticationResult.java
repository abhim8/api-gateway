package gateway.auth.dto;

import java.util.List;
import java.util.Map;

public record AuthenticationResult(
    boolean authenticated,
    String subject,
    List<String> roles,
    List<String> permissions,
    Map<String, Object> claims
) {

    public static AuthenticationResult unauthenticated() {
        return new AuthenticationResult(false, null, List.of(), List.of(), Map.of());
    }

    public static AuthenticationResult authenticated(String subject) {
        return new AuthenticationResult(true, subject, List.of(), List.of(), Map.of());
    }

    public AuthenticationResult {
        if (roles == null) roles = List.of();
        if (permissions == null) permissions = List.of();
        if (claims == null) claims = Map.of();
    }
}
