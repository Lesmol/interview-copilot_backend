package dev.interviewcopilot.backend.speechtokens;

import java.util.List;

import dev.interviewcopilot.backend.auth.CurrentUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
class SpeechTokenController {

    private final List<SpeechTokenIssuer> issuers;
    private final CurrentUserResolver users;

    SpeechTokenController(List<SpeechTokenIssuer> issuers, CurrentUserResolver users) {
        this.issuers = issuers;
        this.users = users;
    }

    @GetMapping("/stt-token")
    SpeechToken token(@RequestParam(defaultValue = "deepgram") String provider,
                      JwtAuthenticationToken auth,
                      @RequestHeader(value = "X-User-Id", required = false) String userId,
                      HttpServletRequest request) {
        users.resolve(auth, userId, request.getRequestURI());
        return issuers.stream()
                .filter(i -> i.provider().equals(provider))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unsupported speech provider: " + provider))
                .issue();
    }
}
