package gateway.config;

import gateway.auth.AuthenticationProvider;
import gateway.auth.MockAuthenticationProvider;
import gateway.auth.RemoteAuthenticationProvider;
import gateway.auth.properties.RemoteAuthenticationProperties;
import io.netty.channel.ChannelOption;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
@EnableConfigurationProperties(RemoteAuthenticationProperties.class)
public class AuthenticationConfiguration {

    static final String AUTH_PROVIDER_PROPERTY = "gateway.authentication.provider";
    static final String AUTH_PROVIDER_MOCK = "mock";
    static final String AUTH_PROVIDER_REMOTE = "remote";

    @Bean
    @ConditionalOnProperty(name = AUTH_PROVIDER_PROPERTY, havingValue = AUTH_PROVIDER_MOCK, matchIfMissing = true)
    public AuthenticationProvider mockAuthenticationProvider() {
        return new MockAuthenticationProvider();
    }

    @Bean
    @ConditionalOnProperty(name = AUTH_PROVIDER_PROPERTY, havingValue = AUTH_PROVIDER_REMOTE)
    public WebClient remoteAuthWebClient(RemoteAuthenticationProperties properties) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) properties.getConnectTimeout().toMillis())
                .responseTimeout(properties.getReadTimeout());

        return WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = AUTH_PROVIDER_PROPERTY, havingValue = AUTH_PROVIDER_REMOTE)
    public AuthenticationProvider remoteAuthenticationProvider(WebClient remoteAuthWebClient) {
        return new RemoteAuthenticationProvider(remoteAuthWebClient);
    }
}
