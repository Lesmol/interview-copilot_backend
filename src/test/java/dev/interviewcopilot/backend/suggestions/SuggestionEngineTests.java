package dev.interviewcopilot.backend.suggestions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import dev.interviewcopilot.backend.sessions.Session;
import dev.interviewcopilot.backend.sessions.SessionService;
import dev.interviewcopilot.backend.sessions.Utterance;
import dev.interviewcopilot.backend.sessions.UtteranceAdded;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SuggestionEngineTests {

    private static final String VAGUE =
            "{\"type\":\"vague\",\"line\":2,\"quote\":\"some cuts\",\"text\":\"Which cuts, and how big?\"}\n";
    private static final String ASIDE =
            "{\"type\":\"aside\",\"line\":1,\"quote\":\"the lawsuit\",\"text\":\"Can we go back to the lawsuit?\"}\n";

    private final UUID sessionId = UUID.randomUUID();
    private SessionService sessions;
    private SuggestionRepository repository;
    private SuggestionBroker broker;
    private final List<Suggestion> stored = new ArrayList<>();
    private final List<Suggestion> published = new ArrayList<>();
    private long now = 1_000_000;

    @BeforeEach
    void setUp() {
        sessions = mock(SessionService.class);
        repository = mock(SuggestionRepository.class);
        broker = mock(SuggestionBroker.class);
        when(sessions.requireOwned("u1", sessionId)).thenReturn(session(Set.of("follow_up", "vague", "contradiction", "aside"),
                Session.Frequency.ACTIVE));
        when(sessions.utterancesAfter(any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of(
                utterance(1, "Dana", "Before the lawsuit we were fine, honestly.", 0, 4_000),
                utterance(2, "Dana", "We had to make some cuts, you know, here and there.", 5_000, 9_000)));
        when(repository.recent(any(), org.mockito.ArgumentMatchers.anyInt())).thenAnswer(i -> List.copyOf(stored));
        org.mockito.Mockito.doAnswer(i -> { stored.add(i.getArgument(0)); return null; })
                .when(repository).insert(any());
        org.mockito.Mockito.doAnswer(i -> { published.add(i.getArgument(0)); return null; })
                .when(broker).publish(any());
    }

    private SuggestionEngine engine(SuggestionModel model) {
        return new SuggestionEngine(sessions, model, repository, broker,
                new SuggestionProperties("k", null, "m", "prompts/journalism.md", 180), () -> now);
    }

    private static SuggestionModel replying(String... chunks) {
        return new SuggestionModel() {
            public void suggest(String prompt, Consumer<String> onText) {
                for (String c : chunks) {
                    onText.accept(c);
                }
            }
            public String summarise(String prompt) { return "summary"; }
        };
    }

    @Test
    void publishesSuggestionFromAVagueAnswerAndStoresIt() {
        engine(replying(VAGUE.substring(0, 30), VAGUE.substring(30))).on(new UtteranceAdded(sessionId, "u1", 2));

        assertThat(published).hasSize(1);
        assertThat(published.get(0).type()).isEqualTo("vague");
        assertThat(published.get(0).sourceSeqs()).containsExactly(2);
        assertThat(published.get(0).status()).isEqualTo("open");
        assertThat(stored).hasSize(1);
    }

    @Test
    void firstSuggestionLeavesBeforeTheModelHasFinished() {
        List<Integer> publishedWhenSecondChunkArrived = new ArrayList<>();
        SuggestionModel model = new SuggestionModel() {
            public void suggest(String prompt, Consumer<String> onText) {
                onText.accept(VAGUE);
                publishedWhenSecondChunkArrived.add(published.size());
                onText.accept(ASIDE);
            }
            public String summarise(String prompt) { return ""; }
        };
        engine(model).on(new UtteranceAdded(sessionId, "u1", 2));

        assertThat(publishedWhenSecondChunkArrived).containsExactly(1);
        assertThat(published).hasSize(2);
    }

    @Test
    void dropsASuggestionThatRepeatsAnExistingOne() {
        stored.add(new Suggestion(UUID.randomUUID(), sessionId, "vague", "Which cuts exactly, and how big were they?",
                "some cuts", List.of(2), "dismissed", Instant.now()));
        engine(replying(VAGUE)).on(new UtteranceAdded(sessionId, "u1", 2));

        assertThat(published).isEmpty();
        verify(repository, never()).insert(any());
    }

    @Test
    void dropsDisabledTypesAndCapsTheCountAtTheFrequencyLimit() {
        when(sessions.requireOwned("u1", sessionId)).thenReturn(session(Set.of("vague"), Session.Frequency.CALM));
        engine(replying(ASIDE, VAGUE, VAGUE.replace("Which cuts, and how big?", "Name one project that lost money."),
                VAGUE.replace("some cuts", "elsewhere").replace("Which cuts, and how big?", "Who decided?")))
                .on(new UtteranceAdded(sessionId, "u1", 2));

        assertThat(published).hasSize(1);
        assertThat(published.get(0).type()).isEqualTo("vague");
    }

    @Test
    void doesNotCallTheModelAgainWhileOneCallIsInFlight() throws Exception {
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        SuggestionModel slow = new SuggestionModel() {
            public void suggest(String prompt, Consumer<String> onText) {
                calls.incrementAndGet();
                inside.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            public String summarise(String prompt) { return ""; }
        };
        SuggestionEngine engine = engine(slow);
        Thread first = Thread.ofVirtual().start(() -> engine.on(new UtteranceAdded(sessionId, "u1", 2)));
        assertThat(inside.await(5, TimeUnit.SECONDS)).isTrue();

        engine.on(new UtteranceAdded(sessionId, "u1", 2)); // arrives mid-call: queued, not run in parallel
        assertThat(calls.get()).isEqualTo(1);

        release.countDown();
        first.join(5_000);
        assertThat(calls.get()).isEqualTo(2); // exactly one catch-up call after the first finished
    }

    @Test
    void skipsVeryShortUtterances() {
        when(sessions.utterancesAfter(any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(utterance(1, "Dana", "Yeah, right.", 0, 1_000)));
        AtomicInteger calls = new AtomicInteger();
        engine(new SuggestionModel() {
            public void suggest(String prompt, Consumer<String> onText) { calls.incrementAndGet(); }
            public String summarise(String prompt) { return ""; }
        }).on(new UtteranceAdded(sessionId, "u1", 1));

        assertThat(calls.get()).isZero();
    }

    @Test
    void aFailingModelDoesNotBreakTheNextUtterance() {
        AtomicInteger calls = new AtomicInteger();
        SuggestionEngine engine = engine(new SuggestionModel() {
            public void suggest(String prompt, Consumer<String> onText) {
                if (calls.incrementAndGet() == 1) {
                    throw new IllegalStateException("boom");
                }
                onText.accept(VAGUE);
            }
            public String summarise(String prompt) { return ""; }
        });
        engine.on(new UtteranceAdded(sessionId, "u1", 2));
        now += 10_000;
        engine.on(new UtteranceAdded(sessionId, "u1", 2));

        assertThat(published).hasSize(1);
    }

    private Session session(Set<String> types, Session.Frequency frequency) {
        return new Session(sessionId, "u1", "Budget story", "Dana", "Where did the money go?", "in_person",
                types, frequency, "", 0, Instant.now(), null);
    }

    private Utterance utterance(int seq, String speaker, String text, long start, long end) {
        return new Utterance(UUID.randomUUID(), sessionId, seq, speaker, text, start, end);
    }
}
