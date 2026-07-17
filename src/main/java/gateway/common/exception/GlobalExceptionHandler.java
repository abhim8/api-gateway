package gateway.common.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import gateway.common.exception.ExceptionMapper.MappedError;
import gateway.common.util.HeaderConstants;
import gateway.filter.CorrelationIdGlobalFilter;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Component
@Order(-2)
@Slf4j
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    private static final String UNKNOWN_METHOD = "UNKNOWN";

    private final ObjectMapper objectMapper;
    private final ExceptionMapper exceptionMapper;

    public GlobalExceptionHandler() {
        this.objectMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        this.exceptionMapper = new ExceptionMapper();
    }

    GlobalExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.exceptionMapper = new ExceptionMapper();
    }

    @Override
    public @NonNull Mono<Void> handle(ServerWebExchange exchange, @NonNull Throwable throwable) {
        if (exchange.getResponse().isCommitted()) {
            log.error("Response already committed for request {}, cannot write error response",
                    exchange.getRequest().getPath().value(), throwable);
            return Mono.error(throwable);
        }

        MappedError mapped = exceptionMapper.resolve(throwable, exchange);
        HttpStatus status = mapped.errorCode().httpStatus();

        logError(status, exchange, throwable);

        ErrorResponse errorResponse = buildErrorResponse(exchange, mapped, status);

        return writeResponse(exchange, status, errorResponse);
    }

    private ErrorResponse buildErrorResponse(ServerWebExchange exchange, MappedError mapped, HttpStatus status) {
        String method = exchange.getRequest().getMethod() != null
                ? exchange.getRequest().getMethod().name() : null;
        String path = exchange.getRequest().getPath().value();
        String correlationId = resolveCorrelationId(exchange);

        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .code(String.valueOf(mapped.errorCode()))
                .message(mapped.message())
                .method(method)
                .path(path)
                .correlationId(correlationId)
                .build();
    }

    private Mono<Void> writeResponse(ServerWebExchange exchange, HttpStatus status, ErrorResponse errorResponse) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String correlationId = errorResponse.correlationId();
        if (correlationId != null) {
            exchange.getResponse().getHeaders().set(HeaderConstants.X_CORRELATION_ID, correlationId);
        }

        DataBufferFactory bufferFactory = exchange.getResponse().bufferFactory();
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(errorResponse);
            DataBuffer buffer = bufferFactory.wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (Exception e) {
            log.error("Failed to serialize error response", e);
            byte[] fallback = ("{\"status\":" + status.value() + "}").getBytes();
            return exchange.getResponse().writeWith(Mono.just(bufferFactory.wrap(fallback)));
        }
    }

    private void logError(HttpStatus status, ServerWebExchange exchange, Throwable throwable) {
        String method = exchange.getRequest().getMethod() != null
                ? exchange.getRequest().getMethod().name() : UNKNOWN_METHOD;
        String path = exchange.getRequest().getPath().value();
        String correlationId = resolveCorrelationId(exchange);

        if (status == HttpStatus.NOT_FOUND) {
            log.debug("Request {} {} {} failed with 404", method, path, correlationId != null ? correlationId : "");
        } else if (status.is4xxClientError()) {
            log.warn("Request {} {} {} failed with {} {}", method, path,
                    correlationId != null ? correlationId : "",
                    status.value(), throwable.getMessage() != null ? throwable.getMessage() : "");
        } else {
            log.error("Request {} {} {} failed with {} {}", method, path,
                    correlationId != null ? correlationId : "",
                    status.value(), throwable.getMessage() != null ? throwable.getMessage() : "",
                    throwable);
        }
    }

    private static String resolveCorrelationId(ServerWebExchange exchange) {
        String attr = exchange.getAttribute(CorrelationIdGlobalFilter.CORRELATION_ID_ATTRIBUTE);
        if (attr != null) {
            return attr;
        }
        String header = exchange.getRequest().getHeaders().getFirst(HeaderConstants.X_CORRELATION_ID);
        if (header != null && !header.isBlank()) {
            return header;
        }
        return null;
    }

}
