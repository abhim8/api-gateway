package gateway.resiliency.circuitbreaker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "gateway.circuit-breaker.enabled=true")
class CircuitBreakerConfigurationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void shouldCreateCustomizerBean() {
        assertThat(context.containsBean("circuitBreakerCustomizer")).isTrue();
        assertThat(context.getBean("circuitBreakerCustomizer"))
                .isInstanceOf(Consumer.class);
    }
}
