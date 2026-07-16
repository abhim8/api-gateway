package gateway.common.exception;

import org.springframework.core.codec.CodecException;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import java.net.ConnectException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

public class ExceptionMapper {

    private final Map<Class<?>, ErrorCode> exceptionMappings = new LinkedHashMap<>();
    private final Map<HttpStatus, ErrorCode> statusMappings = new LinkedHashMap<>();

    public ExceptionMapper() {
        registerException(WebClientResponseException.Unauthorized.class, ErrorCode.UNAUTHORIZED);
        registerException(WebClientResponseException.Forbidden.class, ErrorCode.FORBIDDEN);
        registerException(WebClientResponseException.NotFound.class, ErrorCode.NOT_FOUND);
        registerException(WebClientResponseException.BadRequest.class, ErrorCode.INVALID_REQUEST);
        registerException(WebClientResponseException.MethodNotAllowed.class, ErrorCode.METHOD_NOT_ALLOWED);
        registerException(WebClientResponseException.UnsupportedMediaType.class, ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        registerException(WebClientResponseException.TooManyRequests.class, ErrorCode.RATE_LIMIT_EXCEEDED);

        registerException(CodecException.class, ErrorCode.MALFORMED_JSON);
        registerException(TimeoutException.class, ErrorCode.TIMEOUT);
        registerException(ConnectException.class, ErrorCode.DOWNSTREAM_UNAVAILABLE);

        registerStatus(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST);
        registerStatus(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
        registerStatus(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN);
        registerStatus(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
        registerStatus(HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED);
        registerStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        registerStatus(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMIT_EXCEEDED);
        registerStatus(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.SERVICE_UNAVAILABLE);
        registerStatus(HttpStatus.BAD_GATEWAY, ErrorCode.DOWNSTREAM_UNAVAILABLE);
        registerStatus(HttpStatus.GATEWAY_TIMEOUT, ErrorCode.TIMEOUT);
        registerStatus(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR);
    }

    public void registerException(Class<?> exceptionClass, ErrorCode errorCode) {
        exceptionMappings.put(exceptionClass, errorCode);
    }

    public void registerStatus(HttpStatus status, ErrorCode errorCode) {
        statusMappings.put(status, errorCode);
    }

    public MappedError resolve(Throwable throwable, ServerWebExchange exchange) {
        String message = resolveMessage(throwable);

        ErrorCode code = resolveByExceptionClass(throwable);
        if (code != null) {
            return new MappedError(code, message);
        }

        if (throwable instanceof WebClientResponseException wcre) {
            HttpStatus status = HttpStatus.resolve(wcre.getStatusCode().value());
            if (status != null) {
                code = statusMappings.get(status);
                if (code != null) {
                    return new MappedError(code, message);
                }
            }
        }

        if (throwable instanceof ResponseStatusException rse) {
            code = resolveByStatus(rse);
            if (code != null) {
                String reason = rse.getReason();
                if (reason != null && !reason.isBlank()) {
                    message = reason;
                }
                return new MappedError(code, message);
            }
        }

        return new MappedError(ErrorCode.INTERNAL_ERROR, message);
    }

    private ErrorCode resolveByExceptionClass(Throwable throwable) {
        Class<?> clazz = throwable.getClass();
        while (clazz != null && clazz != Throwable.class) {
            ErrorCode code = exceptionMappings.get(clazz);
            if (code != null) {
                return code;
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    private ErrorCode resolveByStatus(ResponseStatusException rse) {
        HttpStatus status = HttpStatus.resolve(rse.getStatusCode().value());
        if (status != null) {
            ErrorCode code = statusMappings.get(status);
            if (code != null) {
                return code;
            }
        }
        return null;
    }

    private static String resolveMessage(Throwable throwable) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        if (throwable instanceof ResponseStatusException rse) {
            status = HttpStatus.resolve(rse.getStatusCode().value());
        }
        boolean isClientError = status != null && status.is4xxClientError();
        if (isClientError && throwable.getMessage() != null) {
            return throwable.getMessage();
        }
        return null;
    }

    public record MappedError(ErrorCode errorCode, String message) {
    }
}
