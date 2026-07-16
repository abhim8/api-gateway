package gateway.auth.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Setter
@Getter
@ConfigurationProperties(prefix = "gateway.authentication.remote")
public class RemoteAuthenticationProperties {

    private String baseUrl;
    private Duration connectTimeout;
    private Duration readTimeout;
}
