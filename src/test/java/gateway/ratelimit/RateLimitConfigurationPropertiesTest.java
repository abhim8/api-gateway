package gateway.ratelimit;

import gateway.ratelimit.properties.RateLimitConfigurationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = RateLimitConfigurationProperties.class)
class RateLimitConfigurationPropertiesTest {

    @Autowired
    private RateLimitConfigurationProperties properties;

    @Test
    void shouldHaveDefaultEnabledFalse() {
        assertThat(properties.isEnabled()).isFalse();
    }

    @Test
    void shouldHaveDefaultReplenishRate() {
        assertThat(properties.getReplenishRate()).isOne();
    }

    @Test
    void shouldHaveDefaultBurstCapacity() {
        assertThat(properties.getBurstCapacity()).isOne();
    }

    @Test
    void shouldHaveDefaultRequestedTokens() {
        assertThat(properties.getRequestedTokens()).isOne();
    }

    @Test
    void shouldHaveDefaultDenyEmptyKeyTrue() {
        assertThat(properties.isDenyEmptyKey()).isTrue();
    }

    @Test
    void shouldHaveDefaultEmptyKeyStatus() {
        assertThat(properties.getEmptyKeyStatus()).isEqualTo(401);
    }
}
