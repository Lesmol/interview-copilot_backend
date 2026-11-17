package dev.interviewcopilot.backend.speechtokens;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/** Mints a temporary Deepgram access token with the server-side API key. */
@Component
@EnableConfigurationProperties(SpeechProperties.class)
class DeepgramTokenIssuer implements SpeechTokenIssuer {

    private record Grant(String access_token, Integer expires_in) {
    }

    private final SpeechProperties.Deepgram config;
    private final RestClient client;

    DeepgramTokenIssuer(SpeechProperties properties) {
        this.config = properties.deepgram();
        this.client = RestClient.builder().baseUrl(config.baseUrl()).build();
    }

    @Override
    public String provider() {
        return "deepgram";
    }

    @Override
    public SpeechToken issue() {
        if (config.apiKey() == null || config.apiKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Deepgram is not configured");
        }
        Grant grant = client.post().uri("/v1/auth/grant")
                .header("Authorization", "Token " + config.apiKey())
                .body(java.util.Map.of("ttl_seconds", config.tokenTtlSeconds()))
                .retrieve().body(Grant.class);
        if (grant == null || grant.access_token() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Deepgram returned no token");
        }
        return new SpeechToken(provider(), grant.access_token(),
                grant.expires_in() != null ? grant.expires_in() : config.tokenTtlSeconds());
    }
}
