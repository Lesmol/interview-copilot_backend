# interview-copilot_backend

Spring Modulith backend: auth, sessions, questions, speech tokens, suggestions, transcripts, notes. Spring Boot 4.1 / Java 25, Postgres (Flyway), S3.

Milestone 1 contains the `auth` module: it validates the service token, trusts `X-User-Id` only with scope `interview-copilot/act-as-user`, upserts the user, and writes an audit log line with the client id and user id. `GET /api/v1/whoami` returns both.

The `accounts` module handles sign-up and password reset through the IdP's admin API (`POST /api/v1/accounts/sign-up`, `/password-reset/request`, `/password-reset/confirm`). Reset links are emailed (SMTP) and the token is stored only as a SHA-256 hash, single-use, 30 minutes. The `speechtokens` module mints short-lived Deepgram tokens.

`ModularityTests` runs `ApplicationModules.of(Application.class).verify()`; it fails the build when a module reaches into another's internals.

```
./mvnw verify
```

Configuration is environment variables only; see `.env.example`. Licence: Apache-2.0.

## API versioning

Every HTTP API is versioned in the path: `/api/v1/...`. The version in the path is the contract version, separate from the artifact version. Each OpenAPI document carries the same contract version in `info.version` (currently `1.0.0`).

- Compatible changes (new endpoints, new optional fields) keep `/api/v1` and bump the minor or patch of `info.version`.
- A breaking change adds `/api/v2` next to `/api/v1`; both are served until consumers have moved, and the old one is removed in a later release.
- The gateways route only `/api/v1/**`, so a version nobody has deployed yet is a clean 404 at the edge, not a surprise further in.
- Actuator health endpoints are operational and unversioned.
