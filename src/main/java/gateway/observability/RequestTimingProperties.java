package gateway.observability;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Setter
@Getter
@ConfigurationProperties(prefix = "gateway.logging.request-timing")
public class RequestTimingProperties {

    private boolean enabled = true;

    private Duration slowRequestThreshold = Duration.ofMillis(1000);

}
