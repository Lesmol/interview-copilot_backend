package dev.interviewcopilot.backend.accounts;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/** Keycloak admin REST API, called with the backend's own service account (client credentials). */
@Component
@EnableConfigurationProperties(IdentityProperties.class)
class KeycloakIdentityProvider implements IdentityProvider {

    private record Token(String access_token, int expires_in) {
    }

    private record User(String id) {
    }

    private final IdentityProperties config;
    private final RestClient client = RestClient.create();
    private String accessToken;
    private Instant accessTokenExpiry = Instant.EPOCH;

    KeycloakIdentityProvider(IdentityProperties config) {
        this.config = config;
    }

    private synchronized String adminToken() {
        if (accessToken == null || Instant.now().isAfter(accessTokenExpiry.minusSeconds(15))) {
            var form = new LinkedMultiValueMap<String, String>();
            form.add("client_id", config.clientId());
            if (config.usesPasswordGrant()) {
                form.add("grant_type", "password");
                form.add("username", config.username());
                form.add("password", config.password());
            } else {
                form.add("grant_type", "client_credentials");
                form.add("client_secret", config.clientSecret());
            }
            Token token = client.post().uri(config.tokenUri())
                    .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(Token.class);
            accessToken = token.access_token();
            accessTokenExpiry = Instant.now().plusSeconds(token.expires_in());
        }
        return accessToken;
    }

    /** Runs an admin call; a 401 means the cached token is stale (IdP restarted or key rotated), so refresh once and retry. */
    private <T> T admin(java.util.function.Function<String, T> call) {
        try {
            return call.apply(adminToken());
        } catch (HttpClientErrorException.Unauthorized e) {
            synchronized (this) {
                accessToken = null;
            }
            return call.apply(adminToken());
        }
    }

    @Override
    public String createUser(String email, String name, String password) {
        var body = Map.of(
                "username", email, "email", email, "firstName", name,
                "enabled", true, "emailVerified", true,
                "credentials", List.of(Map.of("type", "password", "value", password, "temporary", false)));
        try {
            var response = admin(token -> client.post().uri(config.adminBaseUrl() + "/users")
                    .headers(h -> h.setBearerAuth(token)).body(body).retrieve().toBodilessEntity());
            String location = response.getHeaders().getFirst("Location");
            return location.substring(location.lastIndexOf('/') + 1);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) throw new EmailAlreadyRegistered();
            if (e.getStatusCode() == HttpStatus.BAD_REQUEST) throw new PasswordRejected();
            throw e;
        }
    }

    @Override
    public Optional<String> findUserIdByEmail(String email) {
        List<User> users = admin(token -> client.get()
                .uri(config.adminBaseUrl() + "/users?exact=true&email={email}", email)
                .headers(h -> h.setBearerAuth(token))
                .retrieve().body(new org.springframework.core.ParameterizedTypeReference<List<User>>() {
                }));
        return users == null || users.isEmpty() ? Optional.empty() : Optional.of(users.getFirst().id());
    }

    @Override
    public void setPassword(String userId, String password) {
        try {
            admin(token -> client.put().uri(config.adminBaseUrl() + "/users/{id}/reset-password", userId)
                    .headers(h -> h.setBearerAuth(token))
                    .body(Map.of("type", "password", "value", password, "temporary", false))
                    .retrieve().toBodilessEntity());
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.BAD_REQUEST) throw new PasswordRejected();
            throw e;
        }
    }
}
