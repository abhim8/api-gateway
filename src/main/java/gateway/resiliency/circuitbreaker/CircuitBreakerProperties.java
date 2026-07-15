package gateway.resiliency.circuitbreaker;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Setter
@Getter
@ConfigurationProperties(prefix = "gateway.circuit-breaker")
public class CircuitBreakerProperties {

    private boolean enabled = false;

    private CircuitBreakerConfigProperties defaults = new CircuitBreakerConfigProperties();

    private Map<String, RouteCircuitBreakerConfig> routes = new HashMap<>();

    @Setter
    @Getter
    public static class CircuitBreakerConfigProperties {
        private Integer slidingWindowSize;
        private Integer minimumNumberOfCalls;
        private Float failureRateThreshold;
        private Duration waitDurationInOpenState;
        private Integer permittedNumberOfCallsInHalfOpenState;
        private Boolean automaticTransitionFromOpenToHalfOpenEnabled;
        private Float slowCallRateThreshold;
        private Duration slowCallDurationThreshold;
    }

    @Setter
    @Getter
    public static class RouteCircuitBreakerConfig extends CircuitBreakerConfigProperties {
        private boolean enabled;
        private String circuitBreakerName;
        private String fallbackUri;
    }
}
