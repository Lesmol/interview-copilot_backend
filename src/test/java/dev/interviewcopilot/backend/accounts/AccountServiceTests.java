package dev.interviewcopilot.backend.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AccountServiceTests {

    static class FakeIdentity implements IdentityProvider {
        final Map<String, String> idsByEmail = new HashMap<>();
        final Map<String, String> passwords = new HashMap<>();
        boolean rejectPasswords;

        public String createUser(String email, String name, String password) {
            if (idsByEmail.containsKey(email)) throw new EmailAlreadyRegistered();
            if (rejectPasswords) throw new PasswordRejected();
            String id = "id-" + email;
            idsByEmail.put(email, id);
            passwords.put(id, password);
            return id;
        }

        public Optional<String> findUserIdByEmail(String email) {
            return Optional.ofNullable(idsByEmail.get(email));
        }

        public void setPassword(String userId, String password) {
            if (rejectPasswords) throw new PasswordRejected();
            passwords.put(userId, password);
        }
    }

    static class FakeStore implements ResetTokenStore {
        record Entry(String userId, Instant expiresAt, boolean used) {
        }

        final Map<String, Entry> entries = new HashMap<>();

        public void save(String tokenHash, String userId, Instant expiresAt) {
            entries.put(tokenHash, new Entry(userId, expiresAt, false));
        }

        public Optional<String> consume(String tokenHash, Instant now) {
            Entry e = entries.get(tokenHash);
            if (e == null || e.used() || !e.expiresAt().isAfter(now)) return Optional.empty();
            entries.put(tokenHash, new Entry(e.userId(), e.expiresAt(), true));
            return Optional.of(e.userId());
        }
    }

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-17T10:00:00Z");

        public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(java.time.ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }

    FakeIdentity identity = new FakeIdentity();
    FakeStore store = new FakeStore();
    MutableClock clock = new MutableClock();
    List<String> mails = new ArrayList<>();
    AccountService service;

    @BeforeEach
    void setUp() {
        ResetMailer mailer = (email, link) -> mails.add(email + " " + link);
        service = new AccountService(identity, store, mailer,
                new AccountsProperties("http://app.test", "no-reply@test", 30), clock);
    }

    private String tokenFromLastMail() {
        String link = mails.getLast().split(" ")[1];
        return link.substring(link.indexOf("token=") + 6);
    }

    @Test
    void signUpNormalisesEmailAndRejectsDuplicates() {
        service.signUp("  Dana@Example.test ", "Dana", "correct horse battery");
        assertThat(identity.idsByEmail).containsKey("dana@example.test");
        assertThatThrownBy(() -> service.signUp("dana@example.test", "Dana", "correct horse battery"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
    }

    @Test
    void signUpReportsRejectedPasswords() {
        identity.rejectPasswords = true;
        assertThatThrownBy(() -> service.signUp("a@example.test", "A", "password1234"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void resetRequestForUnknownEmailSendsNothingAndDoesNotFail() {
        service.requestPasswordReset("nobody@example.test");
        assertThat(mails).isEmpty();
        assertThat(store.entries).isEmpty();
    }

    @Test
    void resetEmailCarriesATokenWhoseHashIsStoredNotTheTokenItself() {
        service.signUp("dana@example.test", "Dana", "correct horse battery");
        service.requestPasswordReset("dana@example.test");
        String token = tokenFromLastMail();
        assertThat(mails.getLast()).startsWith("dana@example.test http://app.test/reset-password?token=");
        assertThat(store.entries).containsKey(AccountService.hash(token)).doesNotContainKey(token);
    }

    @Test
    void confirmChangesThePasswordOnceOnly() {
        service.signUp("dana@example.test", "Dana", "correct horse battery");
        service.requestPasswordReset("dana@example.test");
        String token = tokenFromLastMail();

        service.confirmPasswordReset(token, "a brand new passphrase");
        assertThat(identity.passwords.get("id-dana@example.test")).isEqualTo("a brand new passphrase");
        assertThatThrownBy(() -> service.confirmPasswordReset(token, "another passphrase here"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void expiredAndUnknownTokensAreRejected() {
        service.signUp("dana@example.test", "Dana", "correct horse battery");
        service.requestPasswordReset("dana@example.test");
        String token = tokenFromLastMail();
        clock.now = clock.now.plus(Duration.ofMinutes(31));
        assertThatThrownBy(() -> service.confirmPasswordReset(token, "a brand new passphrase"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.confirmPasswordReset("made-up", "a brand new passphrase"))
                .isInstanceOf(ResponseStatusException.class);
    }
}
