package gateway.auth.dto;

public record AuthValidationResponse(
    boolean authenticated,
    AuthenticationClaims claims
) {
}
