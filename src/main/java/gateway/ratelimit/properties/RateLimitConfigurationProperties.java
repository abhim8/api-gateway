package gateway.ratelimit.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "gateway.rate-limit")
public class RateLimitConfigurationProperties {

    private boolean enabled;

    private int replenishRate = 1;

    private int burstCapacity = 1;

    private int requestedTokens = 1;

    private boolean denyEmptyKey = true;

    private int emptyKeyStatus = 401;
}
