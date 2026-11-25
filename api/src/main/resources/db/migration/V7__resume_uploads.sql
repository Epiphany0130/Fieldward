CREATE TABLE resume_uploads (
    account_id BIGINT NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    request_id UUID NOT NULL,
    file_name VARCHAR(240) NOT NULL,
    content_type TEXT NOT NULL CHECK (content_type IN (
        'application/pdf', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
    )),
    sha256 CHAR(64) NOT NULL,
    extracted_text TEXT NOT NULL CHECK (char_length(extracted_text) <= 30000),
    file_bytes BYTEA NOT NULL CHECK (octet_length(file_bytes) BETWEEN 1 AND 5242880),
    PRIMARY KEY (account_id, request_id)
);

ALTER TABLE resume_versions
    ALTER COLUMN draft_revision DROP NOT NULL,
    ALTER COLUMN content DROP NOT NULL,
    ADD COLUMN upload_request_id UUID,
    ADD CONSTRAINT resume_version_source CHECK (
        (draft_revision IS NOT NULL AND content IS NOT NULL AND upload_request_id IS NULL) OR
        (draft_revision IS NULL AND content IS NULL AND upload_request_id IS NOT NULL)
    ),
    ADD CONSTRAINT resume_version_upload UNIQUE (account_id, upload_request_id),
    ADD CONSTRAINT resume_version_upload_owner FOREIGN KEY (account_id, upload_request_id)
        REFERENCES resume_uploads (account_id, request_id) ON DELETE CASCADE;
