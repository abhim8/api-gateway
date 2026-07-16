package gateway.auth.dto;

public record AuthValidationRequest(String token, String requestId) {
}
