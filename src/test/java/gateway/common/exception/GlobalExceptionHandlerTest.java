package gateway.common.exception;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import gateway.common.util.HeaderConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.codec.CodecException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler(JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .build());
    }

    @Test
    void shouldReturnNotFoundForResponseStatus404() {
        MockServerWebExchange exchange = exchange("/unknown", "GET", null);
        verifyStatus(exchange, new ResponseStatusException(HttpStatus.NOT_FOUND), HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldReturnUnauthorizedForResponseStatus401() {
        MockServerWebExchange exchange = exchange("/api/v1/test", "GET", null);
        verifyStatus(exchange, new ResponseStatusException(HttpStatus.UNAUTHORIZED), HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldReturnForbiddenForResponseStatus403() {
        MockServerWebExchange exchange = exchange("/actuator", "GET", null);
        verifyStatus(exchange, new ResponseStatusException(HttpStatus.FORBIDDEN), HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldReturnBadRequestForResponseStatus400() {
        MockServerWebExchange exchange = exchange("/api/v1/test", "POST", null);
        verifyStatus(exchange, new ResponseStatusException(HttpStatus.BAD_REQUEST), HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldReturnMethodNotAllowedForResponseStatus405() {
        MockServerWebExchange exchange = exchange("/api/v1/test", "GET", null);
        verifyStatus(exchange, new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED), HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void shouldReturnUnsupportedMediaTypeForResponseStatus415() {
        MockServerWebExchange exchange = exchange("/api/v1/test", "PUT", null);
        verifyStatus(exchange, new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE), HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void shouldReturnTooManyRequestsForResponseStatus429() {
        MockServerWebExchange exchange = exchange("/api/v1/test", "GET", null);
        verifyStatus(exchange, new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS), HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void shouldReturnServiceUnavailableForResponseStatus503() {
        MockServerWebExchange exchange = exchange("/api/v1/test", "GET", null);
        verifyStatus(exchange, new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE), HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void shouldReturnBadGatewayForResponseStatus502() {
        MockServerWebExchange exchange = exchange("/api/v1/test", "GET", null);
        verifyStatus(exchange, new ResponseStatusException(HttpStatus.BAD_GATEWAY), HttpStatus.BAD_GATEWAY);
    }

    @Test
    void shouldReturnGatewayTimeoutForResponseStatus504() {
        MockServerWebExchange exchange = exchange("/api/v1/test", "GET", null);
        verifyStatus(exchange, new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT), HttpStatus.GATEWAY_TIMEOUT);
    }

    @Test
    void shouldReturnInternalErrorForUnrecognizedException() {
        MockServerWebExchange exchange = exchange("/api/v1/test", "GET", null);
        verifyStatus(exchange, new RuntimeException("unexpected"), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void shouldReturnUnauthorizedForWebClient401() {
        MockServerWebExchange exchange = exchange("/api/v1/test", "GET", null);
        WebClientResponseException ex = new WebClientResponseException(
                HttpStatus.UNAUTHORIZED.value(), "Unauthorized", HttpHeaders.EMPTY, new byte[0], null);
        verifyStatus(exchange, ex, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldReturnNotFoundForWebClient404() {
        MockServerWebExchange exchange = exchange("/api/v1/test", "GET", null);
        WebClientResponseException ex = new WebClientResponseException(
                HttpStatus.NOT_FOUND.value(), "Not Found", HttpHeaders.EMPTY, new byte[0], null);
        verifyStatus(exchange, ex, HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldReturnForbiddenForWebClient403() {
        MockServerWebExchange exchange = exchange("/api/v1/test", "GET", null);
        WebClientResponseException ex = new WebClientResponseException(
                HttpStatus.FORBIDDEN.value(), "Forbidden", HttpHeaders.EMPTY, new byte[0], null);
        verifyStatus(exchange, ex, HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldReturnBadRequestForWebClient400() {
        MockServerWebExchange exchange = exchange("/api/v1/test", "POST", null);
        WebClientResponseException ex = new WebClientResponseException(
                HttpStatus.BAD_REQUEST.value(), "Bad Request", HttpHeaders.EMPTY, new byte[0], null);
        verifyStatus(exchange, ex, HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldReturnTooManyRequestsForWebClient429() {
        MockServerWebExchange exchange = exchange("/api/v1/test", "GET", null);
        WebClientResponseException ex = new WebClientResponseException(
                HttpStatus.TOO_MANY_REQUESTS.value(), "Too Many Requests", HttpHeaders.EMPTY, new byte[0], null);
        verifyStatus(exchange, ex, HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void shouldReturnMalformedJsonForCodecException() {
        MockServerWebExchange exchange = exchange("/api/v1/test", "POST", null);
        verifyStatusAndCode(exchange, new CodecException("Failed to decode"),
                HttpStatus.BAD_REQUEST, "MALFORMED_JSON");
    }

    @Test
    void shouldReturnTimeoutForTimeoutException() {
        MockServerWebExchange exchange = exchange("/api/v1/test", "GET", null);
        verifyStatusAndCode(exchange, new TimeoutException("Read timed out"),
                HttpStatus.GATEWAY_TIMEOUT, "TIMEOUT");
    }

    @Test
    void shouldReturnDownstreamUnavailableForConnectException() {
        MockServerWebExchange exchange = exchange("/api/v1/test", "GET", null);
        verifyStatusAndCode(exchange, new ConnectException("Connection refused"),
                HttpStatus.BAD_GATEWAY, "DOWNSTREAM_UNAVAILABLE");
    }

    @Test
    void shouldIncludeCorrelationIdInResponseHeader() {
        MockServerWebExchange exchange = exchange("/test", "GET", "my-correlation-id");

        Mono<Void> result = handler.handle(exchange, new ResponseStatusException(HttpStatus.NOT_FOUND));
        StepVerifier.create(result).verifyComplete();

        assertThat(exchange.getResponse().getHeaders().getFirst(HeaderConstants.X_CORRELATION_ID))
                .isEqualTo("my-correlation-id");
    }

    @Test
    void shouldNotIncludeCorrelationIdHeaderWhenNotAvailable() {
        MockServerWebExchange exchange = exchange("/test", "GET", null);

        Mono<Void> result = handler.handle(exchange, new ResponseStatusException(HttpStatus.NOT_FOUND));
        StepVerifier.create(result).verifyComplete();

        assertThat(exchange.getResponse().getHeaders().getFirst(HeaderConstants.X_CORRELATION_ID))
                .isNull();
    }

    @Test
    void shouldRespondWithJsonContentType() {
        MockServerWebExchange exchange = exchange("/test", "GET", null);
        Mono<Void> result = handler.handle(exchange, new ResponseStatusException(HttpStatus.NOT_FOUND));
        StepVerifier.create(result).verifyComplete();
        assertThat(exchange.getResponse().getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    void shouldReturnErrorCodeAndMetadataInBody() {
        MockServerWebExchange exchange = exchange("/api/v1/test", "GET", "corr-123");
        handleAndVerifyBody(exchange, new ResponseStatusException(HttpStatus.NOT_FOUND), body -> {
            assertThat(body).containsEntry("code", "NOT_FOUND");
            assertThat(body).containsEntry("status", 404);
            assertThat(body).containsEntry("method", "GET");
            assertThat(body).containsEntry("path", "/api/v1/test");
            assertThat(body).containsEntry("correlationId", "corr-123");
            assertThat(body).containsKey("timestamp");
        });
    }

    @Test
    void shouldReturnReasonAsMessageForClientError() {
        MockServerWebExchange exchange = exchange("/api/v1/test", "GET", null);
        handleAndVerifyBody(exchange,
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payload"), body -> {
                    assertThat(body).containsEntry("message", "Invalid payload");
                });
    }

    @Test
    void shouldNotLeakInternalDetailsForServerError() {
        MockServerWebExchange exchange = exchange("/api/v1/test", "GET", null);
        handleAndVerifyBody(exchange, new RuntimeException("sensitive internal detail"), body -> {
            assertThat(body).containsEntry("code", "INTERNAL_ERROR");
            assertThat(body).doesNotContainKey("message");
        });
    }

    @Test
    void shouldIncludeMethodPathAndCorrelationId() {
        MockServerWebExchange exchange = exchange("/api/v1/templates", "POST", "tx-9876");
        handleAndVerifyBody(exchange, new ResponseStatusException(HttpStatus.FORBIDDEN), body -> {
            assertThat(body).containsEntry("method", "POST");
            assertThat(body).containsEntry("path", "/api/v1/templates");
            assertThat(body).containsEntry("correlationId", "tx-9876");
        });
    }

    @Test
    void shouldFallbackOnSerializationError() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsBytes(any())).thenThrow(new RuntimeException("serialization failed"));

        GlobalExceptionHandler failingHandler = new GlobalExceptionHandler(failingMapper);
        MockServerWebExchange exchange = exchange("/test", "GET", null);

        Mono<Void> result = failingHandler.handle(exchange, new ResponseStatusException(HttpStatus.BAD_REQUEST));
        StepVerifier.create(result).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldReThrowWhenResponseAlreadyCommitted() {
        MockServerWebExchange exchange = exchange("/test", "GET", null);
        exchange.getResponse().setComplete().block();

        Mono<Void> result = handler.handle(exchange, new RuntimeException("downstream error"));

        StepVerifier.create(result).expectError(RuntimeException.class).verify();
    }

    @Test
    void shouldReturnServiceUnavailableFor503() {
        MockServerWebExchange exchange = exchange("/api/v1/test", "GET", null);
        verifyStatusAndCode(exchange,
                new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Remote authentication service unavailable"),
                HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE");
    }

    @Test
    void shouldReturnDownstreamUnavailableForWebClient502() {
        MockServerWebExchange exchange = exchange("/api/v1/test", "GET", null);
        WebClientResponseException ex = new WebClientResponseException(
                HttpStatus.BAD_GATEWAY.value(), "Bad Gateway", HttpHeaders.EMPTY, new byte[0], null);
        verifyStatusAndCode(exchange, ex, HttpStatus.BAD_GATEWAY, "DOWNSTREAM_UNAVAILABLE");
    }

    @Test
    void shouldReturnTimeoutForWebClient504() {
        MockServerWebExchange exchange = exchange("/api/v1/test", "GET", null);
        WebClientResponseException ex = new WebClientResponseException(
                HttpStatus.GATEWAY_TIMEOUT.value(), "Gateway Timeout", HttpHeaders.EMPTY, new byte[0], null);
        verifyStatusAndCode(exchange, ex, HttpStatus.GATEWAY_TIMEOUT, "TIMEOUT");
    }

    private MockServerWebExchange exchange(String path, String method, String correlationId) {
        MockServerHttpRequest.BaseBuilder<?> builder;
        switch (method) {
            case "POST" -> builder = MockServerHttpRequest.post(path);
            case "PUT" -> builder = MockServerHttpRequest.put(path);
            case "DELETE" -> builder = MockServerHttpRequest.delete(path);
            default -> builder = MockServerHttpRequest.get(path);
        }
        if (correlationId != null) {
            builder.header(HeaderConstants.X_CORRELATION_ID, correlationId);
        }
        return MockServerWebExchange.from(builder.build());
    }

    private void verifyStatus(MockServerWebExchange exchange, Throwable throwable, HttpStatus expectedStatus) {
        Mono<Void> result = handler.handle(exchange, throwable);
        StepVerifier.create(result).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(expectedStatus);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }

    private void verifyStatusAndCode(MockServerWebExchange exchange, Throwable throwable,
                                     HttpStatus expectedStatus, String expectedCode) {
        Mono<Void> result = handler.handle(exchange, throwable);
        StepVerifier.create(result).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(expectedStatus);

        Map<String, Object> body = readBody(exchange);
        assertThat(body).containsEntry("code", expectedCode);
    }

    private void handleAndVerifyBody(MockServerWebExchange exchange, Throwable throwable,
                                      java.util.function.Consumer<Map<String, Object>> assertions) {
        Mono<Void> result = handler.handle(exchange, throwable);
        StepVerifier.create(result).verifyComplete();
        Map<String, Object> body = readBody(exchange);
        assertions.accept(body);
    }

    private static Map<String, Object> readBody(ServerWebExchange exchange) {
        MockServerHttpResponse response = (MockServerHttpResponse) exchange.getResponse();
        byte[] bytes = response.getBody()
                .reduce(GlobalExceptionHandlerTest::combine)
                .map(buf -> {
                    byte[] data = new byte[buf.readableByteCount()];
                    buf.read(data);
                    DataBufferUtils.release(buf);
                    return data;
                })
                .blockOptional()
                .orElse(new byte[0]);

        if (bytes.length == 0) return Map.of();
        try {
            return MAPPER.readValue(bytes, MAP_TYPE);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse response: " + new String(bytes, StandardCharsets.UTF_8), e);
        }
    }

    private static DataBuffer combine(DataBuffer a, DataBuffer b) {
        a.write(b);
        DataBufferUtils.release(b);
        return a;
    }
}
