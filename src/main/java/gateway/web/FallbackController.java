package gateway.web;

import gateway.common.util.HeaderConstants;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> fallback(
            @RequestParam(defaultValue = "unknown") String route, ServerWebExchange exchange) {
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
            correlationId = "unknown";
        }

        Map<String, Object> body = Map.of(
                "status",
                503,
                "error",
                "Service Unavailable",
                "route",
                route,
                "correlationId",
                correlationId,
                "timestamp",
                Instant.now().toString());

        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body));
    }
}
