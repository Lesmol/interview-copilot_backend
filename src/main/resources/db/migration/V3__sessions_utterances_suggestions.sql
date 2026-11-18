CREATE TABLE sessions (
    id                 UUID PRIMARY KEY,
    user_id            TEXT NOT NULL REFERENCES users (id),
    title              TEXT NOT NULL,
    interviewee        TEXT,
    angle              TEXT,
    interview_type     TEXT NOT NULL DEFAULT 'in_person',
    suggestion_types   TEXT NOT NULL DEFAULT 'follow_up,vague,contradiction,aside',
    frequency          TEXT NOT NULL DEFAULT 'balanced',
    summary            TEXT NOT NULL DEFAULT '',
    summary_through_seq INT NOT NULL DEFAULT 0,
    started_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at           TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX sessions_user_idx ON sessions (user_id, created_at DESC);

CREATE TABLE utterances (
    id         UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES sessions (id) ON DELETE CASCADE,
    seq        INT NOT NULL,
    speaker    TEXT,
    text       TEXT NOT NULL,
    start_ms   BIGINT NOT NULL,
    end_ms     BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (session_id, seq)
);

CREATE TABLE suggestions (
    id            UUID PRIMARY KEY,
    session_id    UUID NOT NULL REFERENCES sessions (id) ON DELETE CASCADE,
    type          TEXT NOT NULL,
    text          TEXT NOT NULL,
    quote         TEXT,
    source_seqs   TEXT NOT NULL DEFAULT '',
    status        TEXT NOT NULL DEFAULT 'open',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX suggestions_session_idx ON suggestions (session_id, created_at);
