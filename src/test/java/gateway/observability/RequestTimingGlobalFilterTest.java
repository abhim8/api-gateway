package gateway.observability;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RequestTimingGlobalFilterTest {

    private MemoryAppender memoryAppender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        logger = (Logger) LogManager.getLogger(RequestTimingGlobalFilter.class);
        memoryAppender = new MemoryAppender();
        logger.addAppender(memoryAppender);
        logger.setLevel(Level.INFO);
    }

    @AfterEach
    void tearDown() {
        logger.removeAppender(memoryAppender);
    }

    @Test
    void shouldLogTimingOnSuccessfulRequest() {
        RequestTimingGlobalFilter filter = new RequestTimingGlobalFilter(60_000);
        ServerWebExchange exchange = createExchange();
        GatewayFilterChain chain = new OkChain();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        List<LogEvent> events = memoryAppender.getEvents();
        assertThat(events).isNotEmpty();
        String msg = events.get(0).getMessage().getFormattedMessage();
        assertThat(msg).contains("event=\"request-completed\"");
        assertThat(msg).contains("method=\"GET\"");
        assertThat(msg).contains("path=\"/test\"");
        assertThat(msg).contains("status=\"200\"");
        assertThat(events.get(0).getLevel()).isEqualTo(Level.INFO);
    }

    @Test
    void shouldLogTimingOnFailedDownstreamRequest() {
        RequestTimingGlobalFilter filter = new RequestTimingGlobalFilter(60_000);
        ServerWebExchange exchange = createExchange();
        GatewayFilterChain chain = new FailingChain();

        StepVerifier.create(filter.filter(exchange, chain)).expectError().verify();

        List<LogEvent> events = memoryAppender.getEvents();
        assertThat(events).isNotEmpty();
        String msg = events.get(0).getMessage().getFormattedMessage();
        assertThat(msg).contains("event=\"request-completed\"");
        assertThat(msg).contains("method=\"GET\"");
        assertThat(msg).contains("path=\"/test\"");
    }

    @Test
    void shouldLogWarningForSlowRequest() {
        RequestTimingGlobalFilter filter = new RequestTimingGlobalFilter(-1);
        ServerWebExchange exchange = createExchange();
        GatewayFilterChain chain = new OkChain();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        List<LogEvent> events = memoryAppender.getEvents();
        assertThat(events).isNotEmpty();
        assertThat(events.get(0).getLevel()).isEqualTo(Level.WARN);
    }

    @Test
    void shouldLogInfoForFastRequest() {
        RequestTimingGlobalFilter filter = new RequestTimingGlobalFilter(60_000);
        ServerWebExchange exchange = createExchange();
        GatewayFilterChain chain = new OkChain();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        List<LogEvent> events = memoryAppender.getEvents();
        assertThat(events).isNotEmpty();
        assertThat(events.get(0).getLevel()).isEqualTo(Level.INFO);
    }

    @Test
    void shouldHaveOrderMinus110() {
        RequestTimingGlobalFilter filter = new RequestTimingGlobalFilter(1000);

        assertThat(filter.getOrder()).isEqualTo(-110);
    }

    private static ServerWebExchange createExchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/test").build());
    }

    private static class OkChain implements GatewayFilterChain {

        private boolean invoked;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            invoked = true;
            exchange.getResponse().setStatusCode(HttpStatus.OK);
            return Mono.empty();
        }

        boolean wasInvoked() {
            return invoked;
        }
    }

    private static class FailingChain implements GatewayFilterChain {

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            return Mono.error(new RuntimeException("downstream failure"));
        }
    }

    private static class MemoryAppender extends AbstractAppender {

        private final List<LogEvent> events = new ArrayList<>();

        MemoryAppender() {
            super("MemoryAppender", null, null, false, null);
            start();
        }

        @Override
        public void append(LogEvent event) {
            events.add(event.toImmutable());
        }

        List<LogEvent> getEvents() {
            return events;
        }
    }
}
