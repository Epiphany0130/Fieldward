CREATE TABLE direction_change_proposals (
    id UUID PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    old_assessment_id BIGINT NOT NULL,
    direction_id TEXT NOT NULL REFERENCES career_directions(id),
    confirmed_assessment_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (account_id, old_assessment_id) REFERENCES career_assessments(account_id, id) ON DELETE CASCADE,
    FOREIGN KEY (account_id, confirmed_assessment_id) REFERENCES career_assessments(account_id, id) ON DELETE CASCADE
);

CREATE TABLE diagnosis_reports (
    session_id UUID PRIMARY KEY REFERENCES realtime_sessions(id) ON DELETE CASCADE,
    account_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    request_id TEXT NOT NULL,
    input JSONB NOT NULL,
    report JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE diagnosis_session_context (
    session_id UUID PRIMARY KEY REFERENCES realtime_sessions(id) ON DELETE CASCADE,
    direction_id TEXT NOT NULL REFERENCES career_directions(id)
);
