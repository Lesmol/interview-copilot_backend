package dev.interviewcopilot.backend.auth;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class UserDirectory {

    private final JdbcClient jdbc;

    UserDirectory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void ensureExists(String userId) {
        jdbc.sql("INSERT INTO users (id) VALUES (:id) ON CONFLICT (id) DO NOTHING")
                .param("id", userId)
                .update();
    }
}
