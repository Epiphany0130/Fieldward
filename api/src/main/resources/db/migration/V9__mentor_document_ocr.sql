CREATE TABLE mentor_document_ocr (
    account_id BIGINT NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    sha256 CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PROCESSING', 'RETRY', 'READY', 'EMPTY', 'FAILED')),
    attempt INTEGER NOT NULL CHECK (attempt BETWEEN 1 AND 3),
    available_at TIMESTAMPTZ NOT NULL,
    body TEXT CHECK (char_length(body) <= 30000),
    PRIMARY KEY (account_id, sha256),
    CHECK ((status = 'READY' AND body IS NOT NULL AND body <> '') OR (status <> 'READY' AND body IS NULL))
);
CREATE INDEX mentor_document_ocr_search_idx ON mentor_document_ocr USING GIN ((mentor_search_vector(body)));

CREATE VIEW mentor_pdf_scans AS
SELECT account_id, sha256, file_bytes FROM resume_uploads
WHERE content_type = 'application/pdf' AND extracted_text = ''
UNION ALL
SELECT conversation.account_id, file.sha256, file.file_bytes
FROM mentor_files file
JOIN mentor_main_conversations conversation ON conversation.id = file.conversation_id
WHERE file.content_type = 'application/pdf' AND file.extracted_text = '';
