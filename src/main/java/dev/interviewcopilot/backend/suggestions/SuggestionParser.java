package dev.interviewcopilot.backend.suggestions;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Turns the model's streamed text (one JSON object per line) into candidates as each line completes,
 * so the first suggestion can leave before the model has finished the others.
 */
class SuggestionParser {

    record Candidate(SuggestionType type, int line, String quote, String text) {
    }

    private static final Logger log = LoggerFactory.getLogger(SuggestionParser.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final StringBuilder pending = new StringBuilder();
    private final Consumer<Candidate> sink;

    SuggestionParser(Consumer<Candidate> sink) {
        this.sink = sink;
    }

    void accept(String delta) {
        pending.append(delta);
        int newline;
        while ((newline = pending.indexOf("\n")) >= 0) {
            parseLine(pending.substring(0, newline)).ifPresent(sink);
            pending.delete(0, newline + 1);
        }
    }

    /** Call once the stream ends; the last line usually has no trailing newline. */
    void finish() {
        parseLine(pending.toString()).ifPresent(sink);
        pending.setLength(0);
    }

    static List<Candidate> parseAll(String text) {
        List<Candidate> out = new ArrayList<>();
        SuggestionParser parser = new SuggestionParser(out::add);
        parser.accept(text);
        parser.finish();
        return out;
    }

    private static Optional<Candidate> parseLine(String raw) {
        String line = raw.strip();
        if (line.startsWith("```")) {
            return Optional.empty();
        }
        if (!line.startsWith("{")) {
            return Optional.empty();
        }
        try {
            JsonNode node = JSON.readTree(line);
            Optional<SuggestionType> type = SuggestionType.parse(node.path("type").asString(""));
            String text = node.path("text").asString("").strip();
            if (type.isEmpty() || text.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new Candidate(type.get(), node.path("line").asInt(0),
                    node.path("quote").asString("").strip(), text));
        } catch (RuntimeException e) {
            log.warn("Skipping unparseable suggestion line: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
