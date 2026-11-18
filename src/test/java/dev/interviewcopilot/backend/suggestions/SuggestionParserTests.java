package dev.interviewcopilot.backend.suggestions;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class SuggestionParserTests {

    private static final String LINE_1 =
            "{\"type\":\"vague\",\"line\":3,\"quote\":\"some cuts\",\"text\":\"Which cuts exactly?\"}";
    private static final String LINE_2 =
            "{\"type\":\"aside\",\"line\":2,\"quote\":\"the lawsuit\",\"text\":\"Back to the lawsuit?\"}";

    @Test
    void emitsEachLineAsSoonAsItCompletes() {
        List<SuggestionParser.Candidate> out = new ArrayList<>();
        SuggestionParser parser = new SuggestionParser(out::add);
        parser.accept(LINE_1.substring(0, 20));
        assertThat(out).isEmpty();
        parser.accept(LINE_1.substring(20) + "\n" + LINE_2.substring(0, 10));
        assertThat(out).hasSize(1);
        assertThat(out.get(0).type()).isEqualTo(SuggestionType.VAGUE);
        assertThat(out.get(0).line()).isEqualTo(3);
        parser.accept(LINE_2.substring(10));
        assertThat(out).hasSize(1);
        parser.finish();
        assertThat(out).hasSize(2);
        assertThat(out.get(1).quote()).isEqualTo("the lawsuit");
    }

    @Test
    void ignoresFencesProseUnknownTypesAndBrokenJson() {
        String text = "```json\n" + LINE_1 + "\nHere you go!\n{\"type\":\"nonsense\",\"text\":\"x\"}\n{broken\n```\n";
        assertThat(SuggestionParser.parseAll(text)).hasSize(1);
    }

    @Test
    void anEmptyReplyMeansNoSuggestions() {
        assertThat(SuggestionParser.parseAll("")).isEmpty();
    }
}
