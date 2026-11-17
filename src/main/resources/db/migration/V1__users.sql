CREATE TABLE users (
    id         TEXT PRIMARY KEY,
    email      TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
