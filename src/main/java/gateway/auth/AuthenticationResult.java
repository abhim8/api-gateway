package gateway.auth;

public record AuthenticationResult(String principal, boolean authenticated) {

    public static AuthenticationResult unauthenticated() {
        return new AuthenticationResult(null, false);
    }
}
