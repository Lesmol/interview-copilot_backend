package dev.interviewcopilot.backend.suggestions;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import dev.interviewcopilot.backend.sessions.Session;
import dev.interviewcopilot.backend.sessions.SessionService;
import dev.interviewcopilot.backend.sessions.Utterance;
import dev.interviewcopilot.backend.sessions.UtteranceAdded;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Component;

/**
 * The server-side suggestion loop. On each final utterance, unless a call is already running for the session,
 * it sends the last few minutes of transcript plus the running summary to the model and publishes the
 * suggestions that are not repeats. An utterance that arrives mid-call is picked up by one follow-up call.
 */
@Component
class SuggestionEngine {

    private static final Logger log = LoggerFactory.getLogger(SuggestionEngine.class);
    private static final int MIN_WORDS = 4;
    private static final int FOLD_AT = 12;
    private static final int REMEMBER = 30;

    private final SessionService sessions;
    private final SuggestionModel model;
    private final SuggestionRepository repository;
    private final SuggestionBroker broker;
    private final SuggestionProperties properties;
    private final LongSupplier clock;

    private final ConcurrentHashMap<UUID, State> states = new ConcurrentHashMap<>();

    private static final class State {
        boolean running;
        boolean pending;
        long lastFinishedAt;
    }

    @Autowired
    SuggestionEngine(SessionService sessions, SuggestionModel model, SuggestionRepository repository,
                     SuggestionBroker broker, SuggestionProperties properties) {
        this(sessions, model, repository, broker, properties, System::currentTimeMillis);
    }

    SuggestionEngine(SessionService sessions, SuggestionModel model, SuggestionRepository repository,
                     SuggestionBroker broker, SuggestionProperties properties, LongSupplier clock) {
        this.sessions = sessions;
        this.model = model;
        this.repository = repository;
        this.broker = broker;
        this.properties = properties;
        this.clock = clock;
    }

    /** After the utterance is committed, off the request thread, so the POST returns immediately. */
    @Async
    @TransactionalEventListener(fallbackExecution = true)
    void on(UtteranceAdded event) {
        long receivedAt = System.nanoTime();
        State state = states.computeIfAbsent(event.sessionId(), id -> new State());
        synchronized (state) {
            if (state.running) {
                state.pending = true;
                return;
            }
            if (clock.getAsLong() - state.lastFinishedAt < minGap(event, state)) {
                return;
            }
            state.running = true;
        }
        try {
            boolean again;
            do {
                run(event, receivedAt);
                synchronized (state) {
                    state.lastFinishedAt = clock.getAsLong();
                    again = state.pending;
                    state.pending = false;
                    if (!again) {
                        state.running = false;
                    }
                }
                receivedAt = System.nanoTime();
            } while (again);
        } catch (RuntimeException e) {
            log.warn("Suggestion call failed for session {}: {}", event.sessionId(), e.toString());
            synchronized (state) {
                state.running = false;
                state.pending = false;
                state.lastFinishedAt = clock.getAsLong();
            }
        }
    }

    private long minGap(UtteranceAdded event, State state) {
        Session session = sessions.requireOwned(event.userId(), event.sessionId());
        return switch (session.frequency()) {
            case CALM -> 15_000;
            case BALANCED -> 5_000;
            case ACTIVE -> 2_000;
        };
    }

    private void run(UtteranceAdded event, long receivedAtNanos) {
        Session session = sessions.requireOwned(event.userId(), event.sessionId());
        if (session.endedAt() != null) {
            return;
        }
        List<Utterance> unfolded = sessions.utterancesAfter(session.id(), session.summaryThroughSeq());
        if (unfolded.isEmpty()) {
            return;
        }
        Utterance newest = unfolded.get(unfolded.size() - 1);
        if (newest.text().split("\\s+").length < MIN_WORDS) {
            return;
        }
        long windowStart = newest.endMs() - Duration.ofSeconds(properties.windowSeconds()).toMillis();
        List<Utterance> window = unfolded.stream().filter(u -> u.endMs() >= windowStart).toList();
        List<Utterance> older = unfolded.stream().filter(u -> u.endMs() < windowStart).toList();

        List<Suggestion> known = repository.recent(session.id(), REMEMBER);
        List<SuggestionDeduplicator.Known> seen = new ArrayList<>(known.stream()
                .map(s -> new SuggestionDeduplicator.Known(s.type(), s.text(), s.quote())).toList());
        int max = PromptBuilder.maxSuggestions(session.frequency());
        AtomicLong firstTokenNanos = new AtomicLong();
        AtomicLong firstSuggestionNanos = new AtomicLong();
        int[] published = {0};

        SuggestionParser parser = new SuggestionParser(candidate -> {
            if (published[0] >= max || !session.suggestionTypes().contains(candidate.type().wire())) {
                return;
            }
            if (SuggestionDeduplicator.isDuplicate(candidate.type().wire(), candidate.text(), candidate.quote(), seen)) {
                log.debug("Dropped duplicate suggestion for session {}", session.id());
                return;
            }
            Suggestion suggestion = new Suggestion(UUID.randomUUID(), session.id(), candidate.type().wire(),
                    candidate.text(), candidate.quote(),
                    candidate.line() > 0 ? List.of(candidate.line()) : List.of(), "open", Instant.now());
            repository.insert(suggestion);
            seen.add(new SuggestionDeduplicator.Known(suggestion.type(), suggestion.text(), suggestion.quote()));
            published[0]++;
            firstSuggestionNanos.compareAndSet(0, System.nanoTime());
            broker.publish(suggestion);
        });

        model.suggest(PromptBuilder.suggestionPrompt(session, window, known), delta -> {
            firstTokenNanos.compareAndSet(0, System.nanoTime());
            parser.accept(delta);
        });
        parser.finish();
        long done = System.nanoTime();
        log.info("suggestion timings session={} finalTextToFirstToken={}ms finalTextToFirstSuggestion={}ms total={}ms published={}",
                session.id(), millis(receivedAtNanos, firstTokenNanos.get()),
                millis(receivedAtNanos, firstSuggestionNanos.get()), millis(receivedAtNanos, done), published[0]);

        if (older.size() >= FOLD_AT) {
            String summary = model.summarise(PromptBuilder.summaryPrompt(session.summary(), older));
            sessions.saveSummary(session.id(), summary, older.get(older.size() - 1).seq());
        }
    }

    private static String millis(long from, long to) {
        return to == 0 ? "n/a" : String.valueOf((to - from) / 1_000_000);
    }
}
