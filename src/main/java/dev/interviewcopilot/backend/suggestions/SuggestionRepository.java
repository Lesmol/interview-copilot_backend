package dev.interviewcopilot.backend.suggestions;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class SuggestionRepository {

    private final JdbcClient jdbc;

    SuggestionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void insert(Suggestion s) {
        jdbc.sql("""
                INSERT INTO suggestions (id, session_id, type, text, quote, source_seqs, status, created_at)
                VALUES (:id, :session, :type, :text, :quote, :seqs, :status, :created)""")
                .param("id", s.id()).param("session", s.sessionId()).param("type", s.type())
                .param("text", s.text()).param("quote", s.quote())
                .param("seqs", s.sourceSeqs().stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse(""))
                .param("status", s.status()).param("created", Timestamp.from(s.createdAt()))
                .update();
    }

    List<Suggestion> forSession(UUID sessionId) {
        return jdbc.sql("SELECT * FROM suggestions WHERE session_id = :s ORDER BY created_at")
                .param("s", sessionId).query(SuggestionRepository::map).list();
    }

    /** The newest suggestions of any status; used to avoid repeating what was already made or dismissed. */
    List<Suggestion> recent(UUID sessionId, int limit) {
        return jdbc.sql("SELECT * FROM suggestions WHERE session_id = :s ORDER BY created_at DESC LIMIT :n")
                .param("s", sessionId).param("n", limit).query(SuggestionRepository::map).list();
    }

    Optional<Suggestion> find(UUID id) {
        return jdbc.sql("SELECT * FROM suggestions WHERE id = :id").param("id", id)
                .query(SuggestionRepository::map).optional();
    }

    void setStatus(UUID id, String status) {
        jdbc.sql("UPDATE suggestions SET status = :st WHERE id = :id").param("st", status).param("id", id).update();
    }

    private static Suggestion map(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        String seqs = rs.getString("source_seqs");
        List<Integer> sourceSeqs = seqs.isBlank() ? List.of()
                : Arrays.stream(seqs.split(",")).map(Integer::valueOf).toList();
        return new Suggestion(rs.getObject("id", UUID.class), rs.getObject("session_id", UUID.class),
                rs.getString("type"), rs.getString("text"), rs.getString("quote"), sourceSeqs,
                rs.getString("status"), rs.getTimestamp("created_at").toInstant());
    }
}
