package dev.interviewcopilot.backend.suggestions;

import java.util.List;
import java.util.UUID;

import dev.interviewcopilot.backend.auth.CurrentUser;
import dev.interviewcopilot.backend.auth.CurrentUserResolver;
import dev.interviewcopilot.backend.sessions.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1")
class SuggestionsController {

    record UpdateSuggestion(@NotBlank String status) {
    }

    private final SuggestionRepository repository;
    private final SuggestionBroker broker;
    private final SessionService sessions;
    private final CurrentUserResolver users;

    SuggestionsController(SuggestionRepository repository, SuggestionBroker broker, SessionService sessions,
                          CurrentUserResolver users) {
        this.repository = repository;
        this.broker = broker;
        this.sessions = sessions;
        this.users = users;
    }

    @GetMapping("/sessions/{id}/suggestions")
    List<Suggestion> list(@PathVariable UUID id, JwtAuthenticationToken auth,
                          @RequestHeader(value = "X-User-Id", required = false) String userId,
                          HttpServletRequest request) {
        CurrentUser user = users.resolve(auth, userId, request.getRequestURI());
        sessions.requireOwned(user.userId(), id);
        return repository.forSession(id);
    }

    @GetMapping(path = "/sessions/{id}/suggestions/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream(@PathVariable UUID id, JwtAuthenticationToken auth,
                      @RequestHeader(value = "X-User-Id", required = false) String userId,
                      HttpServletRequest request) {
        CurrentUser user = users.resolve(auth, userId, request.getRequestURI());
        sessions.requireOwned(user.userId(), id);
        return broker.subscribe(id);
    }

    @PatchMapping("/suggestions/{id}")
    Suggestion update(@PathVariable UUID id, @Valid @RequestBody UpdateSuggestion body,
                      JwtAuthenticationToken auth,
                      @RequestHeader(value = "X-User-Id", required = false) String userId,
                      HttpServletRequest request) {
        CurrentUser user = users.resolve(auth, userId, request.getRequestURI());
        if (!Suggestion.STATUSES.contains(body.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown status: " + body.status());
        }
        Suggestion suggestion = repository.find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Suggestion not found"));
        sessions.requireOwned(user.userId(), suggestion.sessionId());
        repository.setStatus(id, body.status());
        return repository.find(id).orElseThrow();
    }
}
