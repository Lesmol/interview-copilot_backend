package dev.interviewcopilot.backend.speechtokens;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("speech")
record SpeechProperties(Deepgram deepgram) {

    record Deepgram(String apiKey, String baseUrl, int tokenTtlSeconds) {
    }
}
