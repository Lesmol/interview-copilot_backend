package dev.interviewcopilot.backend.accounts;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("identity.keycloak")
record IdentityProperties(String adminBaseUrl, String tokenUri, String clientId, String clientSecret,
                          String username, String password) {

    /** A username means the password grant (master admin, local workbench); otherwise client credentials. */
    boolean usesPasswordGrant() {
        return username != null && !username.isBlank();
    }
}
