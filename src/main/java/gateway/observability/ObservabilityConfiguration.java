package gateway.observability;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RequestTimingProperties.class)
public class ObservabilityConfiguration {

    @Bean
    @ConditionalOnProperty(
            prefix = "gateway.logging.request-timing",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public GlobalFilter requestTimingGlobalFilter(RequestTimingProperties properties) {
        return new RequestTimingGlobalFilter(properties.getSlowRequestThreshold().toMillis());
    }
}
