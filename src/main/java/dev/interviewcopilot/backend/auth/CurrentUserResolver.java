package dev.interviewcopilot.backend.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Trusts {@code X-User-Id} only when the service token carries the act-as-user scope,
 * and writes an audit line with both the client id and the user id.
 */
@Service
public class CurrentUserResolver {

    private static final Logger audit = LoggerFactory.getLogger("audit");

    private final UserDirectory users;

    CurrentUserResolver(UserDirectory users) {
        this.users = users;
    }

    public CurrentUser resolve(JwtAuthenticationToken token, String userIdHeader, String requestPath) {
        boolean mayActAsUser = token.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(("SCOPE_" + SecurityConfig.ACT_AS_USER_SCOPE)::equals);
        if (!mayActAsUser) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Token may not act as a user");
        }
        if (userIdHeader == null || userIdHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing X-User-Id");
        }
        String clientId = TokenClaims.clientId(token.getToken());
        users.ensureExists(userIdHeader);
        audit.info("clientId={} userId={} path={}", clientId, userIdHeader, requestPath);
        return new CurrentUser(userIdHeader, clientId);
    }
}
