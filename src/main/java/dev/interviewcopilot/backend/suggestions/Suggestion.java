package dev.interviewcopilot.backend.suggestions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

record Suggestion(UUID id, UUID sessionId, String type, String text, String quote, List<Integer> sourceSeqs,
                  String status, Instant createdAt) {

    static final List<String> STATUSES = List.of("open", "asked", "dismissed", "pinned");
}
