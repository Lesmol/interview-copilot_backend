package dev.interviewcopilot.backend.accounts;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("accounts")
record AccountsProperties(String appBaseUrl, String mailFrom, int resetTtlMinutes) {
}
