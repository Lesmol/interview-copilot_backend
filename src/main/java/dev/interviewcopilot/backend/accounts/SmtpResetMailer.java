package dev.interviewcopilot.backend.accounts;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(AccountsProperties.class)
class SmtpResetMailer implements ResetMailer {

    private final JavaMailSender mail;
    private final AccountsProperties config;

    SmtpResetMailer(JavaMailSender mail, AccountsProperties config) {
        this.mail = mail;
        this.config = config;
    }

    @Override
    public void sendResetLink(String email, String link) {
        var message = new SimpleMailMessage();
        message.setFrom(config.mailFrom());
        message.setTo(email);
        message.setSubject("Reset your Interview Co-Pilot password");
        message.setText("""
                Use this link to choose a new password. It works once and expires in %d minutes.

                %s

                If you did not ask for this, you can ignore this email.
                """.formatted(config.resetTtlMinutes(), link));
        mail.send(message);
    }
}
