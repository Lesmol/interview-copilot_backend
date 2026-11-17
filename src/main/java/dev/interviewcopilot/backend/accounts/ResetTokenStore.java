package dev.interviewcopilot.backend.accounts;

import java.time.Instant;
import java.util.Optional;

interface ResetTokenStore {

    void save(String tokenHash, String userId, Instant expiresAt);

    /** Marks a valid, unused, unexpired token as used and returns its user id; empty otherwise. */
    Optional<String> consume(String tokenHash, Instant now);
}
