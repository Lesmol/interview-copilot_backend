package dev.interviewcopilot.backend.accounts;

interface ResetMailer {

    void sendResetLink(String email, String link);
}
