package dev.interviewcopilot.backend.sessions;

import java.util.UUID;

/** One final utterance. {@code seq} numbers the lines of a session from 1 and is what suggestions point back to. */
public record Utterance(UUID id, UUID sessionId, int seq, String speaker, String text, long startMs, long endMs) {
}
