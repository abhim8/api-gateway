package gateway.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public record ErrorResponse(
    LocalDateTime timestamp,
    int status,
    String code,
    String message,
    String method,
    String path,
    String correlationId
) { }
