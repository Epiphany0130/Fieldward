CREATE TABLE resume_evidence_outbox (
    account_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    evidence_id BIGINT NOT NULL REFERENCES capability_evidence(id) ON DELETE CASCADE,
    request_id UUID NOT NULL DEFAULT gen_random_uuid(),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    notified_at TIMESTAMPTZ,
    PRIMARY KEY (account_id, evidence_id),
    UNIQUE (account_id, request_id)
);
CREATE INDEX resume_evidence_outbox_pending ON resume_evidence_outbox(next_attempt_at) WHERE completed_at IS NULL;
