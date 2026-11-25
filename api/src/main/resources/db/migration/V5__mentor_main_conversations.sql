CREATE TABLE mentor_main_conversations (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id BIGINT NOT NULL UNIQUE,
    first_resume_version BIGINT NOT NULL CHECK (first_resume_version = 1),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (account_id, first_resume_version)
        REFERENCES resume_versions (account_id, version) ON DELETE CASCADE
);

INSERT INTO mentor_main_conversations (account_id, first_resume_version, created_at)
SELECT account_id, version, submitted_at
FROM resume_versions
WHERE version = 1;
