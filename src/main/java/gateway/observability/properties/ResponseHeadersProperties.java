package gateway.observability.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "gateway.observability.response-headers")
public class ResponseHeadersProperties {

    private boolean enabled;

    private boolean correlationId;

}
