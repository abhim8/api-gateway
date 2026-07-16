package gateway.auth.dto;

import lombok.Builder;

@Builder
public record AuthValidationRequest(
    String requestId,
    AuthenticationHeaders headers
) {
}
