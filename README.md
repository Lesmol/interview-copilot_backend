# interview-copilot_backend

Spring Modulith backend: auth, sessions, questions, speech tokens, suggestions, transcripts, notes. Spring Boot 4.1 / Java 25, Postgres (Flyway), S3.

Milestone 1 contains the `auth` module: it validates the service token, trusts `X-User-Id` only with scope `interview-copilot/act-as-user`, upserts the user, and writes an audit log line with the client id and user id. `GET /api/whoami` returns both.

`ModularityTests` runs `ApplicationModules.of(Application.class).verify()`; it fails the build when a module reaches into another's internals.

```
./mvnw verify
```

Configuration is environment variables only; see `.env.example`. Licence: Apache-2.0.
