package dev.interviewcopilot.backend.accounts;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcResetTokenStore implements ResetTokenStore {

    private final JdbcClient jdbc;

    JdbcResetTokenStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(String tokenHash, String userId, Instant expiresAt) {
        jdbc.sql("INSERT INTO password_reset_tokens (token_hash, user_id, expires_at) VALUES (:h, :u, :e)")
                .param("h", tokenHash).param("u", userId).param("e", Timestamp.from(expiresAt)).update();
    }

    @Override
    public Optional<String> consume(String tokenHash, Instant now) {
        return jdbc.sql("""
                        UPDATE password_reset_tokens SET used_at = :now
                        WHERE token_hash = :h AND used_at IS NULL AND expires_at > :now
                        RETURNING user_id""")
                .param("h", tokenHash).param("now", Timestamp.from(now))
                .query(String.class).optional();
    }
}
