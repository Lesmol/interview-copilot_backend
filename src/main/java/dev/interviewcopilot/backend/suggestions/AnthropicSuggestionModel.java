package dev.interviewcopilot.backend.suggestions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.ThinkingConfigDisabled;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

@Component
@EnableConfigurationProperties(SuggestionProperties.class)
class AnthropicSuggestionModel implements SuggestionModel {

    private static final String SUMMARY_SYSTEM = """
            You keep a running summary of a live interview for the journalist's co-pilot. Merge the new transcript lines \
            into the existing summary. Keep names, numbers, claims and commitments, and who said them. \
            At most 150 words, plain text, no preamble.""";

    private final SuggestionProperties properties;
    private final String systemPrompt;
    private volatile AnthropicClient client;

    AnthropicSuggestionModel(SuggestionProperties properties) throws IOException {
        this.properties = properties;
        this.systemPrompt = StreamUtils.copyToString(
                new ClassPathResource(properties.promptResource()).getInputStream(),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    private AnthropicClient client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    if (properties.apiKey() == null || properties.apiKey().isBlank()) {
                        throw new IllegalStateException("ANTHROPIC_API_KEY is not set");
                    }
                    var builder = AnthropicOkHttpClient.builder().apiKey(properties.apiKey());
                    if (properties.baseUrl() != null && !properties.baseUrl().isBlank()) {
                        builder.baseUrl(properties.baseUrl());
                    }
                    client = builder.build();
                }
            }
        }
        return client;
    }

    @Override
    public void suggest(String userPrompt, Consumer<String> onText) {
        // The static prompt is marked cacheable; it only starts to hit once it passes the model's minimum
        // cacheable length, so cache_read_input_tokens is expected to stay 0 until the prompt grows.
        MessageCreateParams params = MessageCreateParams.builder()
                .model(properties.model())
                .maxTokens(500L)
                .thinking(ThinkingConfigDisabled.builder().build())
                .systemOfTextBlockParams(List.of(TextBlockParam.builder()
                        .text(systemPrompt)
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build()))
                .addUserMessage(userPrompt)
                .build();
        try (StreamResponse<RawMessageStreamEvent> stream = client().messages().createStreaming(params)) {
            stream.stream()
                    .flatMap(event -> event.contentBlockDelta().stream())
                    .flatMap(delta -> delta.delta().text().stream())
                    .forEach(text -> onText.accept(text.text()));
        }
    }

    @Override
    public String summarise(String userPrompt) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(properties.model())
                .maxTokens(400L)
                .thinking(ThinkingConfigDisabled.builder().build())
                .system(SUMMARY_SYSTEM)
                .addUserMessage(userPrompt)
                .build();
        return client().messages().create(params).content().stream()
                .flatMap(block -> block.text().stream())
                .map(t -> t.text())
                .collect(Collectors.joining())
                .strip();
    }
}
