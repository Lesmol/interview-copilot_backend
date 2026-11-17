package dev.interviewcopilot.backend.auth;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api")
class WhoamiController {

    record Whoami(String userId, String clientId) {
    }

    private final CurrentUserResolver resolver;

    WhoamiController(CurrentUserResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping("/whoami")
    Whoami whoami(JwtAuthenticationToken token,
                  @RequestHeader(value = "X-User-Id", required = false) String userId,
                  HttpServletRequest request) {
        CurrentUser user = resolver.resolve(token, userId, request.getRequestURI());
        return new Whoami(user.userId(), user.clientId());
    }
}
