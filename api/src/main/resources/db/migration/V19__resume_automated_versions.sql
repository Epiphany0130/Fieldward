ALTER TABLE resume_versions ADD COLUMN generated_draft_revision BIGINT;
ALTER TABLE resume_versions DROP CONSTRAINT resume_version_source;
ALTER TABLE resume_versions ADD CONSTRAINT resume_version_source CHECK (
    (draft_revision IS NOT NULL AND content IS NOT NULL AND upload_request_id IS NULL) OR
    (draft_revision IS NULL AND content IS NULL AND upload_request_id IS NOT NULL) OR
    (draft_revision IS NULL AND content IS NOT NULL AND upload_request_id IS NULL)
);
CREATE TABLE resume_evidence_updates (
    account_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    request_id UUID NOT NULL,
    evidence_id BIGINT NOT NULL REFERENCES capability_evidence(id),
    version BIGINT NOT NULL,
    PRIMARY KEY (account_id, request_id),
    UNIQUE (account_id, evidence_id),
    FOREIGN KEY (account_id, version) REFERENCES resume_versions(account_id, version)
);
