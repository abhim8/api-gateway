package gateway.ratelimit.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "gateway.rate-limit")
public class RateLimitConfigurationProperties {

    private boolean enabled;

    private int replenishRate;

    private int burstCapacity;

    private int requestedTokens;

    private boolean denyEmptyKey;

    private int emptyKeyStatus;
}
