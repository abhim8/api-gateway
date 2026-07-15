package gateway.common.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler(new ObjectMapper());
    }

    @Test
    void shouldReturnJsonForNotFound() {
        MockServerWebExchange exchange =
                MockServerWebExchange.from(MockServerHttpRequest.get("/unknown").build());

        Mono<Void> result = handler.handle(exchange, new ResponseStatusException(HttpStatus.NOT_FOUND));

        StepVerifier.create(result).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    void shouldReturnJsonForUnauthorized() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/test").build());

        Mono<Void> result = handler.handle(exchange, new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        StepVerifier.create(result).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldReturnJsonForForbidden() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator").build());

        Mono<Void> result = handler.handle(exchange, new ResponseStatusException(HttpStatus.FORBIDDEN));

        StepVerifier.create(result).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldReturnJsonForInternalServerError() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/test").build());

        Mono<Void> result = handler.handle(exchange, new RuntimeException("unexpected"));

        StepVerifier.create(result).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void shouldIncludeCorrelationIdInResponse() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test")
                .header("X-Correlation-ID", "test-correlation-id")
                .build());

        Mono<Void> result = handler.handle(exchange, new ResponseStatusException(HttpStatus.NOT_FOUND));

        StepVerifier.create(result).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    void shouldFallbackOnSerializationError() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsBytes(any())).thenThrow(new RuntimeException("serialization failed"));

        GlobalExceptionHandler failingHandler = new GlobalExceptionHandler(failingMapper);
        MockServerWebExchange exchange =
                MockServerWebExchange.from(MockServerHttpRequest.get("/test").build());

        Mono<Void> result = failingHandler.handle(exchange, new ResponseStatusException(HttpStatus.BAD_REQUEST));

        StepVerifier.create(result).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
