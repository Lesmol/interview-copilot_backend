package dev.interviewcopilot.backend.suggestions;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decides whether a new suggestion repeats one already made. Two suggestions repeat when they share most of
 * their content words, or when they have the same type and point at the same quote.
 */
final class SuggestionDeduplicator {

    private static final double SIMILARITY = 0.6;
    private static final Set<String> STOP = Set.of("the", "a", "an", "and", "or", "to", "of", "in", "on", "for",
            "that", "this", "it", "is", "was", "were", "be", "you", "your", "did", "do", "does", "what", "how",
            "can", "could", "would", "about", "with", "at", "as", "by", "me", "us", "we", "they", "their");

    private SuggestionDeduplicator() {
    }

    record Known(String type, String text, String quote) {
    }

    static boolean isDuplicate(String type, String text, String quote, Collection<Known> known) {
        Set<String> words = words(text);
        String normQuote = normalise(quote);
        for (Known k : known) {
            if (!normQuote.isEmpty() && k.type().equals(type) && normQuote.equals(normalise(k.quote()))) {
                return true;
            }
            if (jaccard(words, words(k.text())) >= SIMILARITY) {
                return true;
            }
        }
        return false;
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        Set<String> common = new HashSet<>(a);
        common.retainAll(b);
        Set<String> all = new HashSet<>(a);
        all.addAll(b);
        return (double) common.size() / all.size();
    }

    private static Set<String> words(String text) {
        return Arrays.stream(normalise(text).split(" "))
                .filter(w -> w.length() > 1 && !STOP.contains(w))
                .collect(Collectors.toSet());
    }

    private static String normalise(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }
}
