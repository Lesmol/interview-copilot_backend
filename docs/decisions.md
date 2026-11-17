# Decisions

- **`users.id` is text** (the token `sub`), not a generated id, so any OIDC provider's subject works.
- **JdbcClient, not JPA, for now.** The `auth` module only upserts a user row; JPA arrives with sessions (milestone 6) if it earns its keep.
- **Backend OpenAPI spec (`openapi/backend.json`) is deferred to milestone 4**, when the BFF first consumes a backend client. Until then the BFF uses a hand-written DTO for `/api/whoami`.
- **Known trade-off (REPOS.md #7):** the backend trusts the BFF's `X-User-Id`, so a compromised BFF could act as any user. Mitigations in place: the BFF is reachable only from the edge gateway, its token is scope-limited and short-lived (5 minutes), and every call writes an audit line with client id and user id.
- **Claim mapping** lives in one class (`TokenClaims`): `client_id` (Cognito) with `azp` (Keycloak) as fallback. `aud` is not validated on service tokens.
