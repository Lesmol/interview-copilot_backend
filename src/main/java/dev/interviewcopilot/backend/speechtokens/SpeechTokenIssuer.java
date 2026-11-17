package dev.interviewcopilot.backend.speechtokens;

/** One implementation per speech provider; everything above this stays provider-agnostic. */
interface SpeechTokenIssuer {

    String provider();

    SpeechToken issue();
}
