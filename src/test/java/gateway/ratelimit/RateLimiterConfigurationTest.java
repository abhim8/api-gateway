package gateway.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "gateway.rate-limit.enabled=true")
class RateLimiterConfigurationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void shouldCreateGatewayKeyResolver() {
        assertThat(context.containsBean("gatewayKeyResolver")).isTrue();
        assertThat(context.getBean("gatewayKeyResolver")).isInstanceOf(KeyResolver.class);
    }

    @Test
    void shouldCreateRedisRateLimiterWhenRedisAvailable() {
        assertThat(context.containsBean("redisRateLimiter")).isTrue();
        assertThat(context.getBean("redisRateLimiter")).isInstanceOf(RedisRateLimiter.class);
    }
}
