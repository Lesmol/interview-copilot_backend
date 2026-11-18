package dev.interviewcopilot.backend.sessions;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import dev.interviewcopilot.backend.auth.CurrentUser;
import dev.interviewcopilot.backend.auth.CurrentUserResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(SessionsController.class)
class SessionsControllerTests {

    private final UUID sessionId = UUID.randomUUID();

    @Autowired
    MockMvc mvc;
    @MockitoBean
    CurrentUserResolver users;
    @MockitoBean
    SessionService sessions;

    private static RequestPostProcessor service() {
        return jwt().authorities(() -> "SCOPE_interview-copilot/service", () -> "SCOPE_interview-copilot/act-as-user");
    }

    @BeforeEach
    void setUp() {
        when(users.resolve(any(), any(), any())).thenReturn(new CurrentUser("u1", "bff"));
    }

    @Test
    void storesAFinalUtteranceForTheCallingUser() throws Exception {
        when(sessions.addUtterance(eq("u1"), eq(sessionId), eq("Dana"), eq("We made some cuts."), eq(100L), eq(900L)))
                .thenReturn(new Utterance(UUID.randomUUID(), sessionId, 3, "Dana", "We made some cuts.", 100, 900));

        mvc.perform(post("/api/v1/sessions/" + sessionId + "/utterances").header("X-User-Id", "u1").with(service())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"We made some cuts.\",\"speaker\":\"Dana\",\"startMs\":100,\"endMs\":900}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seq").value(3));
    }

    @Test
    void rejectsAnEmptyUtterance() throws Exception {
        mvc.perform(post("/api/v1/sessions/" + sessionId + "/utterances").header("X-User-Id", "u1").with(service())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"  \",\"startMs\":0,\"endMs\":1}"))
                .andExpect(status().isBadRequest());
        verify(sessions, never()).addUtterance(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void createsASessionWithTheCallersUserId() throws Exception {
        when(sessions.create(eq("u1"), eq("Budget story"), any(), any(), any(), any(), any()))
                .thenReturn(new Session(sessionId, "u1", "Budget story", null, null, "in_person",
                        java.util.Set.of("vague"), Session.Frequency.BALANCED, "", 0, java.time.Instant.now(), null));

        mvc.perform(post("/api/v1/sessions").header("X-User-Id", "u1").with(service())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Budget story\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(sessionId.toString()))
                .andExpect(jsonPath("$.frequency").value("balanced"));
    }
}
