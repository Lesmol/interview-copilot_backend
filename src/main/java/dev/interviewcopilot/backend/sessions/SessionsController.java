package dev.interviewcopilot.backend.sessions;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import dev.interviewcopilot.backend.auth.CurrentUser;
import dev.interviewcopilot.backend.auth.CurrentUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sessions")
class SessionsController {

    record CreateSession(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 200) String interviewee,
            @Size(max = 2000) String angle,
            String interviewType,
            Set<String> suggestionTypes,
            String frequency) {
    }

    record SessionView(UUID id, String title, String interviewee, String angle, String interviewType,
                       Set<String> suggestionTypes, String frequency, Instant startedAt, Instant endedAt) {
        static SessionView of(Session s) {
            return new SessionView(s.id(), s.title(), s.interviewee(), s.angle(), s.interviewType(),
                    s.suggestionTypes(), s.frequency().name().toLowerCase(), s.startedAt(), s.endedAt());
        }
    }

    record NewUtterance(
            @NotBlank @Size(max = 5000) String text,
            @Size(max = 100) String speaker,
            @PositiveOrZero long startMs,
            @PositiveOrZero long endMs) {
    }

    record UtteranceView(UUID id, int seq, String speaker, String text, long startMs, long endMs) {
    }

    private final SessionService sessions;
    private final CurrentUserResolver users;

    SessionsController(SessionService sessions, CurrentUserResolver users) {
        this.sessions = sessions;
        this.users = users;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    SessionView create(@Valid @RequestBody CreateSession body, JwtAuthenticationToken auth,
                       @RequestHeader(value = "X-User-Id", required = false) String userId,
                       HttpServletRequest request) {
        CurrentUser user = users.resolve(auth, userId, request.getRequestURI());
        return SessionView.of(sessions.create(user.userId(), body.title(), body.interviewee(), body.angle(),
                body.interviewType(), body.suggestionTypes(), Session.Frequency.parse(body.frequency())));
    }

    @GetMapping("/{id}")
    SessionView get(@PathVariable UUID id, JwtAuthenticationToken auth,
                    @RequestHeader(value = "X-User-Id", required = false) String userId,
                    HttpServletRequest request) {
        CurrentUser user = users.resolve(auth, userId, request.getRequestURI());
        return SessionView.of(sessions.requireOwned(user.userId(), id));
    }

    @PostMapping("/{id}/end")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void end(@PathVariable UUID id, JwtAuthenticationToken auth,
             @RequestHeader(value = "X-User-Id", required = false) String userId,
             HttpServletRequest request) {
        sessions.end(users.resolve(auth, userId, request.getRequestURI()).userId(), id);
    }

    @PostMapping("/{id}/utterances")
    @ResponseStatus(HttpStatus.CREATED)
    UtteranceView addUtterance(@PathVariable UUID id, @Valid @RequestBody NewUtterance body,
                               JwtAuthenticationToken auth,
                               @RequestHeader(value = "X-User-Id", required = false) String userId,
                               HttpServletRequest request) {
        CurrentUser user = users.resolve(auth, userId, request.getRequestURI());
        Utterance u = sessions.addUtterance(user.userId(), id, body.speaker(), body.text(),
                body.startMs(), body.endMs());
        return new UtteranceView(u.id(), u.seq(), u.speaker(), u.text(), u.startMs(), u.endMs());
    }
}
