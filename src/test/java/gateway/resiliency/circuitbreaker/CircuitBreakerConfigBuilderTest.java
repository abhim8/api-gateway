package gateway.resiliency.circuitbreaker;

import gateway.resiliency.circuitbreaker.properties.CircuitBreakerProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class CircuitBreakerConfigBuilderTest {

    @Test
    void shouldBuildWithDefaultsWhenAllNull() {
        CircuitBreakerProperties.CircuitBreakerConfigProperties props =
                new CircuitBreakerProperties.CircuitBreakerConfigProperties();

        CircuitBreakerConfig config = CircuitBreakerConfiguration.buildConfig(props);

        assertThat(config).isNotNull();
        assertThat(config.getSlidingWindowSize()).isEqualTo(10);
        assertThat(config.getMinimumNumberOfCalls()).isEqualTo(5);
        assertThat(config.getFailureRateThreshold()).isEqualTo(50f);
        assertThat(config.getWaitIntervalFunctionInOpenState().apply(1))
                .isEqualTo(Duration.ofSeconds(30).toMillis());
        assertThat(config.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(3);
        assertThat(config.isAutomaticTransitionFromOpenToHalfOpenEnabled()).isTrue();
        assertThat(config.getSlowCallRateThreshold()).isEqualTo(100f);
        assertThat(config.getSlowCallDurationThreshold()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void shouldUseProvidedValues() {
        CircuitBreakerProperties.CircuitBreakerConfigProperties props =
                new CircuitBreakerProperties.CircuitBreakerConfigProperties();
        props.setSlidingWindowSize(25);
        props.setFailureRateThreshold(30f);
        props.setWaitDurationInOpenState(Duration.ofSeconds(10));

        CircuitBreakerConfig config = CircuitBreakerConfiguration.buildConfig(props);

        assertThat(config.getSlidingWindowSize()).isEqualTo(25);
        assertThat(config.getFailureRateThreshold()).isEqualTo(30f);
        assertThat(config.getWaitIntervalFunctionInOpenState().apply(1))
                .isEqualTo(Duration.ofSeconds(10).toMillis());
        assertThat(config.getMinimumNumberOfCalls()).isEqualTo(5);
    }

    @Test
    void shouldReturnNullForNullProps() {
        assertThat(CircuitBreakerConfiguration.buildConfig(null)).isNull();
    }
}
