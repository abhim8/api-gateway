package gateway.config;

import gateway.auth.AuthenticationProvider;
import gateway.auth.MockAuthenticationProvider;
import gateway.auth.RemoteAuthenticationProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthenticationConfiguration {

    @Bean
    @ConditionalOnProperty(name = "gateway.authentication.provider", havingValue = "mock", matchIfMissing = true)
    public AuthenticationProvider mockAuthenticationProvider() {
        return new MockAuthenticationProvider();
    }

    @Bean
    @ConditionalOnProperty(name = "gateway.authentication.provider", havingValue = "remote")
    public AuthenticationProvider remoteAuthenticationProvider() {
        return new RemoteAuthenticationProvider();
    }
}
