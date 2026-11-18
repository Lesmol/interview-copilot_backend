package dev.interviewcopilot.backend.suggestions;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code apiKey} stays server-side; {@code baseUrl} is only for pointing tests or a proxy at another endpoint. */
@ConfigurationProperties("suggestions")
record SuggestionProperties(String apiKey, String baseUrl, String model, String promptResource, int windowSeconds) {
}
