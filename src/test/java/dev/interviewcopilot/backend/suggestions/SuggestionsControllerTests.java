package dev.interviewcopilot.backend.suggestions;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.interviewcopilot.backend.auth.CurrentUser;
import dev.interviewcopilot.backend.auth.CurrentUserResolver;
import dev.interviewcopilot.backend.sessions.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(SuggestionsController.class)
class SuggestionsControllerTests {

    private final UUID sessionId = UUID.randomUUID();
    private final UUID id = UUID.randomUUID();

    @Autowired
    MockMvc mvc;
    @MockitoBean
    CurrentUserResolver users;
    @MockitoBean
    SessionService sessions;
    @MockitoBean
    SuggestionRepository repository;
    @MockitoBean
    SuggestionBroker broker;

    private static RequestPostProcessor service() {
        return jwt().authorities(() -> "SCOPE_interview-copilot/service", () -> "SCOPE_interview-copilot/act-as-user");
    }

    @BeforeEach
    void setUp() {
        when(users.resolve(any(), any(), any())).thenReturn(new CurrentUser("u1", "bff"));
        when(repository.find(id)).thenReturn(Optional.of(
                new Suggestion(id, sessionId, "vague", "Which cuts?", "some cuts", List.of(2), "open", Instant.now())));
    }

    @Test
    void setsTheStatusOfAnOwnedSuggestion() throws Exception {
        mvc.perform(patch("/api/v1/suggestions/" + id).header("X-User-Id", "u1").with(service())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"pinned\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
        verify(repository).setStatus(id, "pinned");
    }

    @Test
    void rejectsAnUnknownStatus() throws Exception {
        mvc.perform(patch("/api/v1/suggestions/" + id).header("X-User-Id", "u1").with(service())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"archived\"}"))
                .andExpect(status().isBadRequest());
        verify(repository, never()).setStatus(any(), any());
    }

    @Test
    void anotherUsersSuggestionLooksLikeItDoesNotExist() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND)).when(sessions).requireOwned("u1", sessionId);
        mvc.perform(patch("/api/v1/suggestions/" + id).header("X-User-Id", "u1").with(service())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"asked\"}"))
                .andExpect(status().isNotFound());
        verify(repository, never()).setStatus(any(), any());
    }
}
