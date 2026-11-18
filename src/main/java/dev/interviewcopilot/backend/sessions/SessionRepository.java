package dev.interviewcopilot.backend.sessions;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class SessionRepository {

    private final JdbcClient jdbc;

    SessionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void insert(Session s) {
        jdbc.sql("""
                INSERT INTO sessions (id, user_id, title, interviewee, angle, interview_type,
                                      suggestion_types, frequency, started_at)
                VALUES (:id, :user, :title, :interviewee, :angle, :type, :types, :frequency, :started)""")
                .param("id", s.id()).param("user", s.userId()).param("title", s.title())
                .param("interviewee", s.interviewee()).param("angle", s.angle())
                .param("type", s.interviewType())
                .param("types", String.join(",", s.suggestionTypes()))
                .param("frequency", s.frequency().name().toLowerCase())
                .param("started", Timestamp.from(s.startedAt()))
                .update();
    }

    Optional<Session> find(UUID id) {
        return jdbc.sql("SELECT * FROM sessions WHERE id = :id").param("id", id)
                .query((rs, n) -> new Session(
                        rs.getObject("id", UUID.class), rs.getString("user_id"), rs.getString("title"),
                        rs.getString("interviewee"), rs.getString("angle"), rs.getString("interview_type"),
                        new LinkedHashSet<>(Arrays.asList(rs.getString("suggestion_types").split(","))),
                        Session.Frequency.parse(rs.getString("frequency")),
                        rs.getString("summary"), rs.getInt("summary_through_seq"),
                        rs.getTimestamp("started_at").toInstant(),
                        rs.getTimestamp("ended_at") == null ? null : rs.getTimestamp("ended_at").toInstant()))
                .optional();
    }

    void end(UUID id) {
        jdbc.sql("UPDATE sessions SET ended_at = now() WHERE id = :id AND ended_at IS NULL")
                .param("id", id).update();
    }

    void saveSummary(UUID id, String summary, int throughSeq) {
        jdbc.sql("UPDATE sessions SET summary = :s, summary_through_seq = :t WHERE id = :id")
                .param("s", summary).param("t", throughSeq).param("id", id).update();
    }

    int addUtterance(Utterance u) {
        return jdbc.sql("""
                INSERT INTO utterances (id, session_id, seq, speaker, text, start_ms, end_ms)
                VALUES (:id, :session, (SELECT COALESCE(MAX(seq), 0) + 1 FROM utterances WHERE session_id = :session),
                        :speaker, :text, :start, :end)
                RETURNING seq""")
                .param("id", u.id()).param("session", u.sessionId()).param("speaker", u.speaker())
                .param("text", u.text()).param("start", u.startMs()).param("end", u.endMs())
                .query(Integer.class).single();
    }

    List<Utterance> utterances(UUID sessionId, int fromSeqExclusive) {
        return jdbc.sql("SELECT * FROM utterances WHERE session_id = :s AND seq > :from ORDER BY seq")
                .param("s", sessionId).param("from", fromSeqExclusive)
                .query((rs, n) -> new Utterance(rs.getObject("id", UUID.class), sessionId, rs.getInt("seq"),
                        rs.getString("speaker"), rs.getString("text"), rs.getLong("start_ms"), rs.getLong("end_ms")))
                .list();
    }
}
