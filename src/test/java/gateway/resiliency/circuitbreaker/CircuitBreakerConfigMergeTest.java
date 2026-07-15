package gateway.resiliency.circuitbreaker;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class CircuitBreakerConfigMergeTest {

    private final CircuitBreakerProperties.CircuitBreakerConfigProperties defaults = create(10, 5, 50f, Duration.ofSeconds(30));

    @Test
    void shouldReturnNullWhenBothNull() {
        assertThat(CircuitBreakerConfiguration.merge(null, null)).isNull();
    }

    @Test
    void shouldMergeOverridesWithDefaults() {
        CircuitBreakerProperties.CircuitBreakerConfigProperties overrides =
                create(20, null, null, null);

        CircuitBreakerProperties.CircuitBreakerConfigProperties result =
                CircuitBreakerConfiguration.merge(defaults, overrides);

        assertThat(result.getSlidingWindowSize()).isEqualTo(20);
        assertThat(result.getMinimumNumberOfCalls()).isEqualTo(5);
        assertThat(result.getFailureRateThreshold()).isEqualTo(50f);
        assertThat(result.getWaitDurationInOpenState()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void shouldUseAllFromOverridesWhenPresent() {
        CircuitBreakerProperties.CircuitBreakerConfigProperties overrides =
                create(25, 10, 30f, Duration.ofSeconds(15));

        CircuitBreakerProperties.CircuitBreakerConfigProperties result =
                CircuitBreakerConfiguration.merge(defaults, overrides);

        assertThat(result.getSlidingWindowSize()).isEqualTo(25);
        assertThat(result.getMinimumNumberOfCalls()).isEqualTo(10);
        assertThat(result.getFailureRateThreshold()).isEqualTo(30f);
        assertThat(result.getWaitDurationInOpenState()).isEqualTo(Duration.ofSeconds(15));
    }

    @Test
    void shouldUseDefaultsWhenOverridesNull() {
        CircuitBreakerProperties.CircuitBreakerConfigProperties result =
                CircuitBreakerConfiguration.merge(defaults, null);

        assertThat(result.getSlidingWindowSize()).isEqualTo(10);
        assertThat(result.getMinimumNumberOfCalls()).isEqualTo(5);
        assertThat(result.getFailureRateThreshold()).isEqualTo(50f);
    }

    @Test
    void shouldUseOverridesWhenDefaultsNull() {
        CircuitBreakerProperties.CircuitBreakerConfigProperties overrides = create(15, null, null, null);

        CircuitBreakerProperties.CircuitBreakerConfigProperties result =
                CircuitBreakerConfiguration.merge(null, overrides);

        assertThat(result.getSlidingWindowSize()).isEqualTo(15);
        assertThat(result.getMinimumNumberOfCalls()).isNull();
    }

    private static CircuitBreakerProperties.CircuitBreakerConfigProperties create(
            Integer slidingWindowSize, Integer minCalls, Float failureRate, Duration waitDuration) {
        CircuitBreakerProperties.CircuitBreakerConfigProperties props =
                new CircuitBreakerProperties.CircuitBreakerConfigProperties();
        props.setSlidingWindowSize(slidingWindowSize);
        props.setMinimumNumberOfCalls(minCalls);
        props.setFailureRateThreshold(failureRate);
        props.setWaitDurationInOpenState(waitDuration);
        return props;
    }
}
