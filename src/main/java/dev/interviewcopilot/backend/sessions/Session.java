package dev.interviewcopilot.backend.sessions;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Exported by the sessions module: the interview a user is running or has run. */
public record Session(
        UUID id,
        String userId,
        String title,
        String interviewee,
        String angle,
        String interviewType,
        Set<String> suggestionTypes,
        Frequency frequency,
        String summary,
        int summaryThroughSeq,
        Instant startedAt,
        Instant endedAt) {

    public enum Frequency {
        CALM, BALANCED, ACTIVE;

        public static Frequency parse(String value) {
            return value == null ? BALANCED : valueOf(value.toUpperCase());
        }
    }
}
