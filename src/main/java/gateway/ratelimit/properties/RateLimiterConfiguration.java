package gateway.ratelimit.properties;

import gateway.ratelimit.GatewayKeyResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "gateway.rate-limit", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(RateLimitConfigurationProperties.class)
public class RateLimiterConfiguration {

    @Bean
    public KeyResolver gatewayKeyResolver() {
        return new GatewayKeyResolver();
    }

    @Bean
    public RedisRateLimiter redisRateLimiter(RateLimitConfigurationProperties properties) {
        return new RedisRateLimiter(
                properties.getReplenishRate(),
                properties.getBurstCapacity(),
                properties.getRequestedTokens());
    }
}
