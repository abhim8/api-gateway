package gateway.auth;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RemoteAuthenticationProviderTest {

    private final RemoteAuthenticationProvider provider = new RemoteAuthenticationProvider();

    @Test
    void shouldThrowUnsupportedOperationException() {
        var thrown = assertThrows(
                UnsupportedOperationException.class, () -> provider.authenticate(null).block());

        org.assertj.core.api.Assertions.assertThat(thrown.getMessage())
                .contains("Auth Platform integration is not implemented yet.");
    }
}
