package dev.interviewcopilot.backend.suggestions;

import java.util.Arrays;
import java.util.Optional;

enum SuggestionType {
    FOLLOW_UP("follow_up"), VAGUE("vague"), CONTRADICTION("contradiction"), ASIDE("aside");

    private final String wire;

    SuggestionType(String wire) {
        this.wire = wire;
    }

    String wire() {
        return wire;
    }

    static Optional<SuggestionType> parse(String value) {
        return Arrays.stream(values()).filter(t -> t.wire.equals(value)).findFirst();
    }
}
