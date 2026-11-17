-- Only a hash of the emailed token is stored; the token itself is single-use and short-lived.
CREATE TABLE password_reset_tokens (
    token_hash TEXT PRIMARY KEY,
    user_id    TEXT        NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX password_reset_tokens_user_idx ON password_reset_tokens (user_id);
