package dev.interviewcopilot.backend.suggestions;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class SuggestionDeduplicatorTests {

    private static final List<SuggestionDeduplicator.Known> KNOWN = List.of(
            new SuggestionDeduplicator.Known("vague", "Which projects did the budget cuts affect, and by how much?",
                    "we had to make some cuts"));

    @Test
    void rephrasingWithMostOfTheSameWordsIsADuplicate() {
        assertThat(SuggestionDeduplicator.isDuplicate("vague",
                "By how much did the budget cuts affect which projects?", "", KNOWN)).isTrue();
    }

    @Test
    void sameTypeAndSameQuoteIsADuplicateEvenWhenWordedDifferently() {
        assertThat(SuggestionDeduplicator.isDuplicate("vague", "Can you name an example of a cut?",
                "We had to make some cuts.", KNOWN)).isTrue();
    }

    @Test
    void sameQuoteWithAnotherTypeIsNotADuplicate() {
        assertThat(SuggestionDeduplicator.isDuplicate("follow_up", "Can you name an example of a cut?",
                "We had to make some cuts.", KNOWN)).isFalse();
    }

    @Test
    void differentQuestionAboutAnotherQuoteIsNew() {
        assertThat(SuggestionDeduplicator.isDuplicate("follow_up",
                "Who signed off on the new contractor?", "the contractor was approved", KNOWN)).isFalse();
    }

    @Test
    void punctuationAndCaseDoNotHideARepeat() {
        assertThat(SuggestionDeduplicator.isDuplicate("vague",
                "WHICH PROJECTS did the budget cuts affect -- and by how much?!", null, KNOWN)).isTrue();
    }

    @Test
    void nothingIsADuplicateOfAnEmptyHistory() {
        assertThat(SuggestionDeduplicator.isDuplicate("aside", "Go back to the lawsuit?", "the lawsuit", List.of()))
                .isFalse();
    }
}
