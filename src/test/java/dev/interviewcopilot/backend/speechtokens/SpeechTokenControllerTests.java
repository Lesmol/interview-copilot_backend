package dev.interviewcopilot.backend.speechtokens;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.interviewcopilot.backend.auth.CurrentUserResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(SpeechTokenController.class)
@Import(SpeechTokenControllerTests.Config.class)
class SpeechTokenControllerTests {

    @org.springframework.boot.test.context.TestConfiguration
    static class Config {
        @org.springframework.context.annotation.Bean
        SpeechTokenIssuer fakeDeepgram() {
            return new SpeechTokenIssuer() {
                public String provider() { return "deepgram"; }
                public SpeechToken issue() { return new SpeechToken("deepgram", "temp-token", 60); }
            };
        }
    }

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CurrentUserResolver users;

    private static RequestPostProcessor service() {
        return jwt().authorities(() -> "SCOPE_interview-copilot/service", () -> "SCOPE_interview-copilot/act-as-user");
    }

    @Test
    void returnsShortLivedTokenNotTheApiKey() throws Exception {
        mvc.perform(get("/api/stt-token?provider=deepgram").header("X-User-Id", "u1").with(service()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("deepgram"))
                .andExpect(jsonPath("$.token").value("temp-token"))
                .andExpect(jsonPath("$.expiresInSeconds").value(60));
    }

    @Test
    void rejectsUnknownProvider() throws Exception {
        mvc.perform(get("/api/stt-token?provider=nope").header("X-User-Id", "u1").with(service()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresServiceToken() throws Exception {
        mvc.perform(get("/api/stt-token").header("X-User-Id", "u1")).andExpect(status().isUnauthorized());
    }
}
