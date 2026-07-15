package gateway.resiliency.circuitbreaker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = CircuitBreakerProperties.class)
class CircuitBreakerPropertiesTest {

    @Autowired
    private CircuitBreakerProperties properties;

    @Test
    void shouldHaveDefaultEnabledFalse() {
        assertThat(properties.isEnabled()).isFalse();
    }

    @Test
    void shouldHaveEmptyRoutes() {
        assertThat(properties.getRoutes()).isEmpty();
    }

    @Test
    void defaultsShouldBeEmpty() {
        assertThat(properties.getDefaults().getSlidingWindowSize()).isNull();
        assertThat(properties.getDefaults().getMinimumNumberOfCalls()).isNull();
        assertThat(properties.getDefaults().getFailureRateThreshold()).isNull();
    }
}
