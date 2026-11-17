package dev.interviewcopilot.backend.accounts;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;

/** Account endpoints that need no signed-in user. The edge gateway exposes only these without a token. */
@RestController
@RequestMapping("/api/v1/accounts")
class AccountsController {

    record SignUpRequest(@NotBlank @Email @Size(max = 254) String email,
                         @NotBlank @Size(max = 100) String name,
                         @NotBlank @Size(min = 10, max = 128) String password) {
    }

    record PasswordResetRequest(@NotBlank @Email @Size(max = 254) String email) {
    }

    record PasswordResetConfirmation(@NotBlank @Size(max = 200) String token,
                                     @NotBlank @Size(min = 10, max = 128) String newPassword) {
    }

    private final AccountService accounts;

    AccountsController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping("/sign-up")
    @ResponseStatus(HttpStatus.CREATED)
    void signUp(@Valid @RequestBody SignUpRequest request) {
        accounts.signUp(request.email(), request.name(), request.password());
    }

    @PostMapping("/password-reset/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void requestReset(@Valid @RequestBody PasswordResetRequest request) {
        accounts.requestPasswordReset(request.email());
    }

    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void confirmReset(@Valid @RequestBody PasswordResetConfirmation request) {
        accounts.confirmPasswordReset(request.token(), request.newPassword());
    }

    @Configuration
    static class ClockConfig {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }
}
