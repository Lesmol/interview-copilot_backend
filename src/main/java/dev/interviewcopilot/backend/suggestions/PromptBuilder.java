package dev.interviewcopilot.backend.suggestions;

import java.util.List;
import java.util.stream.Collectors;

import dev.interviewcopilot.backend.sessions.Session;
import dev.interviewcopilot.backend.sessions.Utterance;

/** Builds the per-call part of the prompt; the static instructions live in the prompt resource. */
final class PromptBuilder {

    private PromptBuilder() {
    }

    static int maxSuggestions(Session.Frequency frequency) {
        return switch (frequency) {
            case CALM -> 1;
            case BALANCED -> 2;
            case ACTIVE -> 3;
        };
    }

    static String suggestionPrompt(Session session, List<Utterance> window, List<Suggestion> alreadyMade) {
        StringBuilder sb = new StringBuilder();
        sb.append("Story: ").append(orNone(session.title())).append('\n');
        sb.append("Interviewee: ").append(orNone(session.interviewee())).append('\n');
        sb.append("Angle: ").append(orNone(session.angle())).append('\n');
        sb.append("Enabled types: ").append(String.join(", ", session.suggestionTypes())).append('\n');
        sb.append("Maximum suggestions: ").append(maxSuggestions(session.frequency())).append("\n\n");
        if (!session.summary().isBlank()) {
            sb.append("Summary of earlier conversation:\n").append(session.summary()).append("\n\n");
        }
        sb.append("Recent transcript (line number, speaker, text):\n");
        for (Utterance u : window) {
            sb.append(u.seq()).append(" | ").append(u.speaker() == null || u.speaker().isBlank() ? "unattributed" : u.speaker())
                    .append(" | ").append(u.text()).append('\n');
        }
        if (!alreadyMade.isEmpty()) {
            sb.append("\nSuggestions already made (do not repeat or rephrase):\n");
            sb.append(alreadyMade.stream().map(s -> "- " + s.text()).collect(Collectors.joining("\n"))).append('\n');
        }
        sb.append("\nNow answer with the JSON lines, newest transcript line first in your attention.");
        return sb.toString();
    }

    static String summaryPrompt(String existingSummary, List<Utterance> older) {
        StringBuilder sb = new StringBuilder("Existing summary:\n")
                .append(existingSummary.isBlank() ? "(none yet)" : existingSummary)
                .append("\n\nNew lines to fold in:\n");
        for (Utterance u : older) {
            sb.append(u.speaker() == null || u.speaker().isBlank() ? "unattributed" : u.speaker())
                    .append(": ").append(u.text()).append('\n');
        }
        return sb.toString();
    }

    private static String orNone(String v) {
        return v == null || v.isBlank() ? "(not given)" : v;
    }
}
