package gateway.common.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import gateway.filter.CorrelationIdGlobalFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

@Component
@Order(-2)
@Slf4j
@RequiredArgsConstructor
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    private ObjectMapper objectMapper = JsonMapper.builder().build();

    @Override
    public @NonNull Mono<Void> handle(ServerWebExchange exchange, @NonNull Throwable throwable) {
        HttpStatusCode status = resolveStatus(throwable);
        String statusText = resolveError(throwable);
        String path = exchange.getRequest().getPath().value();
        String correlationId = resolveCorrelationId(exchange);

        if (exchange.getResponse().isCommitted()) {
            log.error("Response already committed for request {}, cannot write error response", path, throwable);
            return Mono.error(throwable);
        }

        if (status.is5xxServerError()) {
            log.error("Unhandled exception for request {}", path, throwable);
        } else {
            log.warn("Request {} failed with {} {}: {}", path, status.value(), statusText, throwable.getMessage());
        }

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "error", statusText,
                "path", path,
                "correlationId", correlationId);

        DataBufferFactory bufferFactory = exchange.getResponse().bufferFactory();
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            DataBuffer buffer = bufferFactory.wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (Exception e) {
            log.error("Failed to serialize error response", e);
            byte[] fallback = ("{\"status\":" + status.value() + "}").getBytes();
            return exchange.getResponse().writeWith(Mono.just(bufferFactory.wrap(fallback)));
        }
    }

    private static HttpStatusCode resolveStatus(Throwable throwable) {
        if (throwable instanceof ResponseStatusException rse) {
            return rse.getStatusCode();
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private static String resolveError(Throwable throwable) {
        if (throwable instanceof ResponseStatusException rse) {
            String reason = rse.getReason();
            if (reason != null && !reason.isBlank()) {
                return reason;
            }
        }
        return resolveDefaultStatusText(throwable);
    }

    private static String resolveDefaultStatusText(Throwable throwable) {
        HttpStatusCode status = resolveStatus(throwable);
        if (status instanceof HttpStatus hs) {
            return hs.getReasonPhrase();
        }
        return status.toString();
    }

    private static String resolveCorrelationId(ServerWebExchange exchange) {
        String attr = exchange.getAttribute(CorrelationIdGlobalFilter.CORRELATION_ID_ATTRIBUTE);
        if (attr != null) {
            return attr;
        }
        String header = exchange.getRequest().getHeaders().getFirst("X-Correlation-ID");
        if (header != null && !header.isBlank()) {
            return header;
        }
        return "unknown";
    }
}
