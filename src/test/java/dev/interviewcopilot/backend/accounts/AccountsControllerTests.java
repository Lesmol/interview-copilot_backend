package dev.interviewcopilot.backend.accounts;

import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.interviewcopilot.backend.auth.CurrentUserResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(AccountsController.class)
class AccountsControllerTests {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AccountService accounts;

    @MockitoBean
    CurrentUserResolver users;

    private static RequestPostProcessor service() {
        return jwt().authorities(() -> "SCOPE_interview-copilot/service");
    }

    @Test
    void signUpValidatesInput() throws Exception {
        mvc.perform(post("/api/v1/accounts/sign-up").with(service()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"name\":\"Dana\",\"password\":\"long enough password\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/accounts/sign-up").with(service()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dana@example.test\",\"name\":\"Dana\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signUpCreatesTheAccount() throws Exception {
        mvc.perform(post("/api/v1/accounts/sign-up").with(service()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dana@example.test\",\"name\":\"Dana\",\"password\":\"long enough password\"}"))
                .andExpect(status().isCreated());
        verify(accounts).signUp("dana@example.test", "Dana", "long enough password");
    }

    @Test
    void resetRequestIsAcceptedAndConfirmIsNoContent() throws Exception {
        mvc.perform(post("/api/v1/accounts/password-reset/request").with(service()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dana@example.test\"}"))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/v1/accounts/password-reset/confirm").with(service()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"abc\",\"newPassword\":\"long enough password\"}"))
                .andExpect(status().isNoContent());
    }
}
