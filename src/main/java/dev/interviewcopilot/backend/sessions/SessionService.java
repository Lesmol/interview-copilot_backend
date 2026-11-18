package dev.interviewcopilot.backend.sessions;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Exported API of the sessions module. Every read checks ownership by user id. */
@Service
public class SessionService {

    static final Set<String> ALL_TYPES = Set.of("follow_up", "vague", "contradiction", "aside");

    private final SessionRepository repository;
    private final ApplicationEventPublisher events;

    SessionService(SessionRepository repository, ApplicationEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public Session create(String userId, String title, String interviewee, String angle, String interviewType,
                          Set<String> suggestionTypes, Session.Frequency frequency) {
        Set<String> types = suggestionTypes == null || suggestionTypes.isEmpty() ? ALL_TYPES : suggestionTypes;
        if (!ALL_TYPES.containsAll(types)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown suggestion type");
        }
        Session session = new Session(UUID.randomUUID(), userId, title, interviewee, angle,
                interviewType == null ? "in_person" : interviewType, types,
                frequency == null ? Session.Frequency.BALANCED : frequency, "", 0, Instant.now(), null);
        repository.insert(session);
        return session;
    }

    /** Returns the session, or 404 for both "does not exist" and "belongs to someone else". */
    public Session requireOwned(String userId, UUID sessionId) {
        return repository.find(sessionId)
                .filter(s -> s.userId().equals(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
    }

    public void end(String userId, UUID sessionId) {
        requireOwned(userId, sessionId);
        repository.end(sessionId);
    }

    @Transactional
    public Utterance addUtterance(String userId, UUID sessionId, String speaker, String text, long startMs, long endMs) {
        requireOwned(userId, sessionId);
        Utterance draft = new Utterance(UUID.randomUUID(), sessionId, 0, speaker, text, startMs, endMs);
        int seq = repository.addUtterance(draft);
        events.publishEvent(new UtteranceAdded(sessionId, userId, seq));
        return new Utterance(draft.id(), sessionId, seq, speaker, text, startMs, endMs);
    }

    public List<Utterance> utterancesAfter(UUID sessionId, int seq) {
        return repository.utterances(sessionId, seq);
    }

    public void saveSummary(UUID sessionId, String summary, int throughSeq) {
        repository.saveSummary(sessionId, summary, throughSeq);
    }
}
