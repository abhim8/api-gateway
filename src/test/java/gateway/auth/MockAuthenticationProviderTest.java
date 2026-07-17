package gateway.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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

        assertNotNull(result);
        assertEquals("mock-user", result.subject());
        assertTrue(result.roles().isEmpty());
        assertTrue(result.permissions().isEmpty());
        assertNull(result.claims());
    }
}
