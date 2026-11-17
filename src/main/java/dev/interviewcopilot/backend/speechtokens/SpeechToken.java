package dev.interviewcopilot.backend.speechtokens;

/** A short-lived credential for one speech provider. Never the real API key. */
public record SpeechToken(String provider, String token, int expiresInSeconds) {
}
