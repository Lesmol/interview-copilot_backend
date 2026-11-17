package dev.interviewcopilot.backend.accounts;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final IdentityProvider identity;
    private final ResetTokenStore tokens;
    private final ResetMailer mailer;
    private final AccountsProperties config;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    AccountService(IdentityProvider identity, ResetTokenStore tokens, ResetMailer mailer,
                   AccountsProperties config, Clock clock) {
        this.identity = identity;
        this.tokens = tokens;
        this.mailer = mailer;
        this.config = config;
        this.clock = clock;
    }

    void signUp(String email, String name, String password) {
        try {
            identity.createUser(normalise(email), name.trim(), password);
        } catch (IdentityProvider.EmailAlreadyRegistered e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.");
        } catch (IdentityProvider.PasswordRejected e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That password is not allowed. Choose a longer or less common one.");
        }
    }

    /** Always completes quietly so the response never reveals whether the email has an account. */
    void requestPasswordReset(String email) {
        try {
            identity.findUserIdByEmail(normalise(email)).ifPresent(userId -> {
                byte[] raw = new byte[32];
                random.nextBytes(raw);
                String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
                tokens.save(hash(token), userId, clock.instant().plus(Duration.ofMinutes(config.resetTtlMinutes())));
                mailer.sendResetLink(normalise(email), config.appBaseUrl() + "/reset-password?token=" + token);
            });
        } catch (RuntimeException e) {
            log.warn("Password reset request failed", e);
        }
    }

    void confirmPasswordReset(String token, String newPassword) {
        Instant now = clock.instant();
        String userId = tokens.consume(hash(token), now)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "This reset link is invalid or has expired."));
        try {
            identity.setPassword(userId, newPassword);
        } catch (IdentityProvider.PasswordRejected e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That password is not allowed. Choose a longer or less common one.");
        }
    }

    private static String normalise(String email) {
        return email.trim().toLowerCase();
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
