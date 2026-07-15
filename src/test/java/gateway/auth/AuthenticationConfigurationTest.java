package gateway.auth;

import gateway.config.AuthenticationConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@SpringBootTest(classes = AuthenticationConfiguration.class)
class AuthenticationConfigurationTest {

    @Autowired
    private AuthenticationProvider provider;

    @Test
    void shouldCreateMockProviderByDefault() {
        assertInstanceOf(MockAuthenticationProvider.class, provider);
    }

    @SpringBootTest(classes = AuthenticationConfiguration.class)
    @TestPropertySource(properties = "gateway.authentication.provider=mock")
    static class WithMockProperty {

        @Autowired
        private AuthenticationProvider provider;

        @Test
        void shouldCreateMockProvider() {
            assertInstanceOf(MockAuthenticationProvider.class, provider);
        }
    }

    @SpringBootTest(classes = AuthenticationConfiguration.class)
    @TestPropertySource(properties = "gateway.authentication.provider=remote")
    static class WithRemoteProperty {

        @Autowired
        private AuthenticationProvider provider;

        @Test
        void shouldCreateRemoteProvider() {
            assertInstanceOf(RemoteAuthenticationProvider.class, provider);
        }
    }
}
