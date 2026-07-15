package gateway.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.observability.response-headers")
public class ResponseHeadersProperties {

    private boolean enabled = true;

    private boolean correlationId = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(boolean correlationId) {
        this.correlationId = correlationId;
    }
}
