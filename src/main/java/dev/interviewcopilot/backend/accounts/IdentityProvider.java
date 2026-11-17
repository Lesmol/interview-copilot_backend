package dev.interviewcopilot.backend.accounts;

import java.util.Optional;

/** Port to the IdP's user store: Keycloak locally, Cognito in production. */
interface IdentityProvider {

    /** Creates a user and returns its id (the token {@code sub}). Throws {@link EmailAlreadyRegistered}. */
    String createUser(String email, String name, String password);

    Optional<String> findUserIdByEmail(String email);

    void setPassword(String userId, String password);

    class EmailAlreadyRegistered extends RuntimeException {
    }

    class PasswordRejected extends RuntimeException {
    }
}
