package gateway.resiliency.circuitbreaker;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.Builder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConditionalOnProperty(prefix = "gateway.circuit-breaker", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CircuitBreakerProperties.class)
public class CircuitBreakerConfiguration {

    private static final int DEFAULT_SLIDING_WINDOW_SIZE = 10;
    private static final int DEFAULT_MINIMUM_NUMBER_OF_CALLS = 5;
    private static final float DEFAULT_FAILURE_RATE_THRESHOLD = 50;
    private static final Duration DEFAULT_WAIT_DURATION_IN_OPEN_STATE = Duration.ofSeconds(30);
    private static final int DEFAULT_PERMITTED_NUMBER_OF_CALLS_IN_HALF_OPEN_STATE = 3;
    private static final boolean DEFAULT_AUTOMATIC_TRANSITION_FROM_OPEN_TO_HALF_OPEN_ENABLED = true;
    private static final float DEFAULT_SLOW_CALL_RATE_THRESHOLD = 100;
    private static final Duration DEFAULT_SLOW_CALL_DURATION_THRESHOLD = Duration.ofSeconds(60);

    @Bean
    public java.util.function.Consumer<Resilience4JCircuitBreakerFactory> circuitBreakerCustomizer(
            CircuitBreakerProperties properties) {
        return new CircuitBreakerFactoryCustomizer(properties);
    }

    static CircuitBreakerConfig buildConfig(CircuitBreakerProperties.CircuitBreakerConfigProperties props) {
        if (props == null) {
            return null;
        }
        Builder builder = CircuitBreakerConfig.custom();
        applyIfPresent(builder::slidingWindowSize, props.getSlidingWindowSize(), DEFAULT_SLIDING_WINDOW_SIZE);
        applyIfPresent(builder::minimumNumberOfCalls, props.getMinimumNumberOfCalls(), DEFAULT_MINIMUM_NUMBER_OF_CALLS);
        applyIfPresent(builder::failureRateThreshold, props.getFailureRateThreshold(), DEFAULT_FAILURE_RATE_THRESHOLD);
        applyIfPresent(builder::waitDurationInOpenState, props.getWaitDurationInOpenState(), DEFAULT_WAIT_DURATION_IN_OPEN_STATE);
        applyIfPresent(builder::permittedNumberOfCallsInHalfOpenState, props.getPermittedNumberOfCallsInHalfOpenState(), DEFAULT_PERMITTED_NUMBER_OF_CALLS_IN_HALF_OPEN_STATE);
        applyIfPresent(builder::automaticTransitionFromOpenToHalfOpenEnabled, props.getAutomaticTransitionFromOpenToHalfOpenEnabled(), DEFAULT_AUTOMATIC_TRANSITION_FROM_OPEN_TO_HALF_OPEN_ENABLED);
        applyIfPresent(builder::slowCallRateThreshold, props.getSlowCallRateThreshold(), DEFAULT_SLOW_CALL_RATE_THRESHOLD);
        applyIfPresent(builder::slowCallDurationThreshold, props.getSlowCallDurationThreshold(), DEFAULT_SLOW_CALL_DURATION_THRESHOLD);
        return builder.build();
    }

    static CircuitBreakerProperties.CircuitBreakerConfigProperties merge(
            CircuitBreakerProperties.CircuitBreakerConfigProperties defaults,
            CircuitBreakerProperties.CircuitBreakerConfigProperties overrides) {
        if (overrides == null) {
            return defaults;
        }
        if (defaults == null) {
            return overrides;
        }
        CircuitBreakerProperties.CircuitBreakerConfigProperties result =
                new CircuitBreakerProperties.CircuitBreakerConfigProperties();
        result.setSlidingWindowSize(coalesce(overrides.getSlidingWindowSize(), defaults.getSlidingWindowSize()));
        result.setMinimumNumberOfCalls(coalesce(overrides.getMinimumNumberOfCalls(), defaults.getMinimumNumberOfCalls()));
        result.setFailureRateThreshold(coalesce(overrides.getFailureRateThreshold(), defaults.getFailureRateThreshold()));
        result.setWaitDurationInOpenState(coalesce(overrides.getWaitDurationInOpenState(), defaults.getWaitDurationInOpenState()));
        result.setPermittedNumberOfCallsInHalfOpenState(coalesce(overrides.getPermittedNumberOfCallsInHalfOpenState(), defaults.getPermittedNumberOfCallsInHalfOpenState()));
        result.setAutomaticTransitionFromOpenToHalfOpenEnabled(coalesce(overrides.getAutomaticTransitionFromOpenToHalfOpenEnabled(), defaults.getAutomaticTransitionFromOpenToHalfOpenEnabled()));
        result.setSlowCallRateThreshold(coalesce(overrides.getSlowCallRateThreshold(), defaults.getSlowCallRateThreshold()));
        result.setSlowCallDurationThreshold(coalesce(overrides.getSlowCallDurationThreshold(), defaults.getSlowCallDurationThreshold()));
        return result;
    }

    private static <T> void applyIfPresent(java.util.function.Consumer<T> setter, T value, T defaultValue) {
        setter.accept(value != null ? value : defaultValue);
    }

    private static <T> T coalesce(T primary, T fallback) {
        return primary != null ? primary : fallback;
    }
}
