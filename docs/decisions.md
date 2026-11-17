# Decisions

- **`users.id` is text** (the token `sub`), not a generated id, so any OIDC provider's subject works.
- **JdbcClient, not JPA, for now.** The `auth` module only upserts a user row; JPA arrives with sessions (milestone 6) if it earns its keep.
- **Backend OpenAPI spec (`openapi/backend.json`) is deferred to milestone 4**, when the BFF first consumes a backend client. Until then the BFF uses a hand-written DTO for `/api/whoami`.
- **Known trade-off (REPOS.md #7):** the backend trusts the BFF's `X-User-Id`, so a compromised BFF could act as any user. Mitigations in place: the BFF is reachable only from the edge gateway, its token is scope-limited and short-lived (5 minutes), and every call writes an audit line with client id and user id.
- **Claim mapping** lives in one class (`TokenClaims`): `client_id` (Cognito) with `azp` (Keycloak) as fallback. `aud` is not validated on service tokens.
- **Speech tokens (milestone 2):** `GET /api/stt-token` mints a Deepgram temporary token (`/v1/auth/grant`, 60 s TTL) with the server-side key. This needs a key with Member or Admin rights; a key without them gets 403. One `SpeechTokenIssuer` per provider; AssemblyAI arrives in milestone 3.
