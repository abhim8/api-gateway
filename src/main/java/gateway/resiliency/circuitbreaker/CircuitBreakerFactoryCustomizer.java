package gateway.resiliency.circuitbreaker;

import gateway.resiliency.circuitbreaker.properties.CircuitBreakerProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;

import java.util.function.Consumer;

@RequiredArgsConstructor
class CircuitBreakerFactoryCustomizer implements Consumer<Resilience4JCircuitBreakerFactory> {

    private final CircuitBreakerProperties properties;

    @Override
    public void accept(Resilience4JCircuitBreakerFactory factory) {
        CircuitBreakerProperties.CircuitBreakerConfigProperties defaults = properties.getDefaults();

        factory.configureDefault(id -> {
            CircuitBreakerConfig config = CircuitBreakerConfiguration.buildConfig(defaults);
            if (config == null) {
                return null;
            }
            return new Resilience4JConfigBuilder(id).circuitBreakerConfig(config).build();
        });

        properties.getRoutes().forEach((routeId, routeConfig) -> {
            if (routeConfig.isEnabled() && routeConfig.getCircuitBreakerName() != null) {
                CircuitBreakerProperties.CircuitBreakerConfigProperties merged =
                        CircuitBreakerConfiguration.merge(defaults, routeConfig);
                CircuitBreakerConfig config = CircuitBreakerConfiguration.buildConfig(merged);
                if (config != null) {
                    factory.configure(
                            builder -> builder.circuitBreakerConfig(config),
                            routeConfig.getCircuitBreakerName());
                }
            }
        });
    }
}
