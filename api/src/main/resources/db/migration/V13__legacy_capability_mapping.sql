-- Unrecognized legacy topics remain reviewable without inventing a capability identity.
CREATE TABLE legacy_capability_unmapped (
    account_id BIGINT NOT NULL,
    assessment_id BIGINT NOT NULL,
    source_type TEXT NOT NULL,
    source_id TEXT NOT NULL,
    legacy_key TEXT NOT NULL,
    outcome TEXT NOT NULL DEFAULT 'PENDING' CHECK (outcome = 'PENDING'),
    PRIMARY KEY (account_id, source_type, source_id),
    FOREIGN KEY (account_id, assessment_id) REFERENCES career_assessments (account_id, id) ON DELETE CASCADE
);
CREATE TABLE legacy_capability_migrations (
    account_id BIGINT NOT NULL,
    assessment_id BIGINT NOT NULL,
    migration_version INTEGER NOT NULL,
    session_revision INTEGER NOT NULL,
    report_digest TEXT,
    migrated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id, assessment_id),
    FOREIGN KEY (account_id, assessment_id) REFERENCES career_assessments (account_id, id) ON DELETE CASCADE
);
