package gateway.web;

import gateway.common.util.HeaderConstants;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    static final String DEFAULT_UNKNOWN = "unknown";
    static final String FIELD_STATUS = "status";
    static final String FIELD_ERROR = "error";
    static final String FIELD_ROUTE = "route";
    static final String FIELD_CORRELATION_ID = "correlationId";
    static final String FIELD_TIMESTAMP = "timestamp";
    static final String ERROR_SERVICE_UNAVAILABLE = "Service Unavailable";

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> fallback(
            @RequestParam(defaultValue = DEFAULT_UNKNOWN) String route, ServerWebExchange exchange) {
        return buildFallbackResponse(route, exchange);
    }

    @GetMapping(value = "/{route}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> fallbackWithPath(
            @PathVariable String route, ServerWebExchange exchange) {
        return buildFallbackResponse(route, exchange);
    }

    private Mono<ResponseEntity<Map<String, Object>>> buildFallbackResponse(String route, ServerWebExchange exchange) {

        String correlationId = exchange.getRequest().getHeaders().getFirst(HeaderConstants.X_CORRELATION_ID);
        if (correlationId == null) {
            correlationId = DEFAULT_UNKNOWN;
        }

        Map<String, Object> body = Map.of(
                FIELD_STATUS, 503,
                FIELD_ERROR, ERROR_SERVICE_UNAVAILABLE,
                FIELD_ROUTE, route,
                FIELD_CORRELATION_ID, correlationId,
                FIELD_TIMESTAMP, LocalDateTime.now().toString());

        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body));
    }
}
