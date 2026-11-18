package dev.interviewcopilot.backend.suggestions;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Fans new suggestions out to the open SSE streams of a session. */
@Component
class SuggestionBroker {

    private static final Logger log = LoggerFactory.getLogger(SuggestionBroker.class);

    private final ConcurrentHashMap<UUID, List<SseEmitter>> streams = new ConcurrentHashMap<>();

    SseEmitter subscribe(UUID sessionId) {
        SseEmitter emitter = new SseEmitter(0L);
        List<SseEmitter> list = streams.computeIfAbsent(sessionId, id -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        Runnable remove = () -> list.remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        try {
            // Opens the stream through every proxy right away instead of waiting for the first suggestion.
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            remove.run();
        }
        return emitter;
    }

    void publish(Suggestion suggestion) {
        send(suggestion.sessionId(), SseEmitter.event().name("suggestion").id(suggestion.id().toString())
                .data(suggestion, org.springframework.http.MediaType.APPLICATION_JSON));
    }

    /** Keeps idle connections alive through gateways that close silent streams. */
    @Scheduled(fixedDelay = 15_000)
    void heartbeat() {
        streams.keySet().forEach(id -> send(id, SseEmitter.event().comment("keepalive")));
    }

    private void send(UUID sessionId, SseEmitter.SseEventBuilder event) {
        for (SseEmitter emitter : streams.getOrDefault(sessionId, List.of())) {
            try {
                emitter.send(event);
            } catch (IOException | IllegalStateException e) {
                log.debug("Dropping closed suggestion stream for session {}", sessionId);
                streams.get(sessionId).remove(emitter);
            }
        }
    }
}
