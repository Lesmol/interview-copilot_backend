package dev.interviewcopilot.backend.suggestions;

import java.util.function.Consumer;

/** The language-model port. Only {@link AnthropicSuggestionModel} talks to a provider. */
interface SuggestionModel {

    /** Streams the reply to {@code onText} as it arrives. */
    void suggest(String userPrompt, Consumer<String> onText);

    /** One short completion, used to fold old transcript into the running summary. */
    String summarise(String userPrompt);
}
