ALTER TABLE mentor_main_conversations
    ADD COLUMN career_onboarding_required BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE mentor_main_conversations ALTER COLUMN career_onboarding_required SET DEFAULT TRUE;

CREATE TABLE career_assessments (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id BIGINT NOT NULL,
    resume_version BIGINT NOT NULL,
    direction_id VARCHAR(40) NOT NULL,
    source_digest CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('WAITING_TEXT', 'ANALYZING', 'READY', 'FAILED')),
    attempt INTEGER NOT NULL CHECK (attempt > 0),
    report JSONB,
    lease_until TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (account_id, id),
    UNIQUE (account_id, resume_version, direction_id, source_digest),
    FOREIGN KEY (account_id, resume_version) REFERENCES resume_versions (account_id, version) ON DELETE CASCADE,
    CHECK (
        (status = 'ANALYZING' AND lease_until IS NOT NULL AND report IS NULL)
        OR (status = 'READY' AND lease_until IS NULL AND report IS NOT NULL)
        OR (status IN ('WAITING_TEXT', 'FAILED') AND lease_until IS NULL AND report IS NULL)
    )
);

CREATE TABLE career_targets (
    account_id BIGINT PRIMARY KEY REFERENCES accounts (id) ON DELETE CASCADE,
    assessment_id BIGINT NOT NULL,
    FOREIGN KEY (account_id, assessment_id) REFERENCES career_assessments (account_id, id) ON DELETE CASCADE
);

ALTER TABLE mentor_turns ALTER COLUMN student_message DROP NOT NULL;
ALTER TABLE mentor_turns ADD CONSTRAINT mentor_turns_assistant_opening
    CHECK (student_message IS NOT NULL OR (status = 'COMPLETED' AND side_chat_id IS NULL));
