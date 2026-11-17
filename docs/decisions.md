# Decisions

- **`users.id` is text** (the token `sub`), not a generated id, so any OIDC provider's subject works.
- **JdbcClient, not JPA, for now.** The `auth` module only upserts a user row; JPA arrives with sessions (milestone 6) if it earns its keep.
- **Backend OpenAPI spec (`openapi/backend.json`) is deferred to milestone 4**, when the BFF first consumes a backend client. Until then the BFF uses a hand-written DTO for `/api/whoami`.
- **Known trade-off (REPOS.md #7):** the backend trusts the BFF's `X-User-Id`, so a compromised BFF could act as any user. Mitigations in place: the BFF is reachable only from the edge gateway, its token is scope-limited and short-lived (5 minutes), and every call writes an audit line with client id and user id.
- **Claim mapping** lives in one class (`TokenClaims`): `client_id` (Cognito) with `azp` (Keycloak) as fallback. `aud` is not validated on service tokens.
- **Speech tokens (milestone 2):** `GET /api/stt-token` mints a Deepgram temporary token (`/v1/auth/grant`, 60 s TTL) with the server-side key. This needs a key with Member or Admin rights; a key without them gets 403. One `SpeechTokenIssuer` per provider; AssemblyAI arrives in milestone 3.
- **AssemblyAI skipped (product owner).** Milestone 3 is reduced to what already exists: the provider-agnostic adapter interface and `SpeechTokenIssuer` per provider stay, with Deepgram as the only implementation. A second provider can be added later behind the same two interfaces.
- **API versioning:** all routes are under `/api/v1`; `info.version` in the OpenAPI document is the contract version (`1.0.0`). See the README.
- **Accounts module.** Sign-up and password reset go through an `IdentityProvider` port (Keycloak admin REST today; a Cognito adapter later). Choices to review:
  - Sign-up marks the email as verified without sending a verification email, so someone could register another person's address. The owner can still recover the account through password reset (the emailed link proves ownership), but sign-up with a verification step is the better production design.
  - Sign-up answers 409 when an email is taken, which reveals that an account exists. Password reset never does (always 202).
  - Reset tokens: 32 random bytes, only the SHA-256 hash is stored, single use, 30-minute expiry, consumed atomically in SQL.
  - Both account routes are public at the edge with a stricter rate limit (20 per minute per client address).
- **Local IdP admin access uses Keycloak's master admin (password grant).** A service-account client in the app realm was tried first, but Keycloak 26.8 rejected its token on the admin API ("Expected audience not available in the token") even with the realm-management roles and several audiences. Local only; production should use a narrowly scoped identity (for Cognito, an IAM role limited to user administration). The provider refreshes its admin token and retries once on 401.
