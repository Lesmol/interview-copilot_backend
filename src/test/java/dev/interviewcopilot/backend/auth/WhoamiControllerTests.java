package dev.interviewcopilot.backend.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WhoamiController.class)
@Import({SecurityConfig.class, CurrentUserResolver.class})
class WhoamiControllerTests {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    UserDirectory users;

    @Test
    void rejectsMissingToken() throws Exception {
        mvc.perform(get("/api/v1/whoami").header("X-User-Id", "u1")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTokenWithoutServiceScope() throws Exception {
        mvc.perform(get("/api/v1/whoami").header("X-User-Id", "u1")
                        .with(jwt().authorities(() -> "SCOPE_other")))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsUserHeaderWithoutActAsUserScope() throws Exception {
        mvc.perform(get("/api/v1/whoami").header("X-User-Id", "u1")
                        .with(jwt().authorities(() -> "SCOPE_interview-copilot/service")))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsUserIdAndClientId() throws Exception {
        mvc.perform(get("/api/v1/whoami").header("X-User-Id", "u1")
                        .with(jwt().jwt(j -> j.claim("azp", "interview-copilot-bff"))
                                .authorities(() -> "SCOPE_interview-copilot/service",
                                        () -> "SCOPE_interview-copilot/act-as-user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("u1"))
                .andExpect(jsonPath("$.clientId").value("interview-copilot-bff"));
    }

    @Test
    void rejectsMissingUserId() throws Exception {
        mvc.perform(get("/api/v1/whoami")
                        .with(jwt().authorities(() -> "SCOPE_interview-copilot/service",
                                () -> "SCOPE_interview-copilot/act-as-user")))
                .andExpect(status().isBadRequest());
    }
}
