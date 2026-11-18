package dev.interviewcopilot.backend.sessions;

import java.util.UUID;

/** Published after a final utterance is stored; the suggestions module reacts to it. */
public record UtteranceAdded(UUID sessionId, String userId, int seq) {
}
