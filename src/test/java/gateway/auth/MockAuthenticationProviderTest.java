package gateway.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MockAuthenticationProviderTest {

    private final MockAuthenticationProvider provider = new MockAuthenticationProvider();

    @Test
    void shouldReturnAuthenticatedResult() {
        var result = provider.authenticate(null).block();

        assertNotNull(result);
        assertTrue(result.authenticated());
    }

    @Test
    void shouldReturnExpectedDefaultValues() {
        var result = provider.authenticate(null).block();

        assertEquals("mock-user", result.subject());
        assertTrue(result.roles().isEmpty());
        assertTrue(result.permissions().isEmpty());
        assertEquals(Map.of(), result.claims());
    }
}
