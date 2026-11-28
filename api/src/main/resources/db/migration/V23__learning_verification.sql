CREATE TABLE learning_verifications (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    capability_id TEXT NOT NULL REFERENCES career_capabilities(id),
    request_id TEXT NOT NULL CHECK (btrim(request_id) <> ''),
    question TEXT,
    claim_id TEXT,
    lease_until TIMESTAMPTZ,
    answer TEXT,
    outcome TEXT CHECK (outcome IN ('PASSED','NEEDS_PRACTICE')),
    feedback TEXT,
    evidence_id BIGINT REFERENCES capability_evidence(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(account_id, request_id),
    CHECK ((answer IS NULL AND outcome IS NULL AND feedback IS NULL AND evidence_id IS NULL)
        OR (answer IS NOT NULL AND outcome IS NOT NULL AND feedback IS NOT NULL AND evidence_id IS NOT NULL))
);
