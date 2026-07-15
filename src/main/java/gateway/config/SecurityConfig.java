package gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http.authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.GET, "/actuator/health/**")
                        .permitAll()
                        .pathMatchers(HttpMethod.GET, "/actuator/info")
                        .permitAll()
                        .pathMatchers("/fallback/**")
                        .permitAll()
                        .pathMatchers("/api/v1/**")
                        .authenticated()
                        .pathMatchers("/actuator/**")
                        .hasRole("ADMIN")
                        .anyExchange()
                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
                .csrf(csrf -> csrf.disable())
                .build();
    }
}
