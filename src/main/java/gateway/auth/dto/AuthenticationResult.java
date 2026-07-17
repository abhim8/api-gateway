package gateway.auth.dto;

import org.springframework.http.HttpHeaders;

import java.util.List;

public record AuthenticationResult(
    boolean authenticated,
    String subject,
    List<String> roles,
    List<String> permissions,
    AuthenticationClaims claims,
    HttpHeaders relayResponseHeaders
) {

    public static AuthenticationResult unauthenticated() {
        return new AuthenticationResult(false, null, List.of(), List.of(), null, new HttpHeaders());
    }

    public static AuthenticationResult authenticated(String subject) {
        return new AuthenticationResult(true, subject, List.of(), List.of(), null, new HttpHeaders());
    }

    public AuthenticationResult {
        if (roles == null) roles = List.of();
        if (permissions == null) permissions = List.of();
        if (relayResponseHeaders == null) relayResponseHeaders = new HttpHeaders();
    }
}
