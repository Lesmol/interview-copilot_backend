package dev.interviewcopilot.backend.auth;

/**
 * The calling user, resolved from the service token plus {@code X-User-Id}.
 * Exported API of the auth module: other modules authorise data by {@link #userId()}.
 */
public record CurrentUser(String userId, String clientId) {
}
