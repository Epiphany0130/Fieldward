CREATE TABLE resume_versions (
    account_id BIGINT NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    version BIGINT NOT NULL CHECK (version > 0),
    draft_revision BIGINT NOT NULL CHECK (draft_revision > 0),
    content JSONB NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id, version),
    UNIQUE (account_id, draft_revision)
);
