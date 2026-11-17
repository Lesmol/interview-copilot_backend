package dev.interviewcopilot.backend.auth;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The only place that knows IdP-specific claim names (Keycloak uses {@code azp},
 * Cognito client-credentials tokens use {@code client_id}).
 */
final class TokenClaims {

    private TokenClaims() {
    }

    static String clientId(Jwt jwt) {
        String clientId = jwt.getClaimAsString("client_id");
        return clientId != null ? clientId : jwt.getClaimAsString("azp");
    }
}
