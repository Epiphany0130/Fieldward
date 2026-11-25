CREATE TABLE mentor_side_chats (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES mentor_main_conversations (id) ON DELETE CASCADE,
    title VARCHAR(80) NOT NULL CHECK (btrim(title) <> ''),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (conversation_id, id),
    UNIQUE (conversation_id, title)
);

ALTER TABLE mentor_turns
    ADD COLUMN side_chat_id BIGINT,
    ADD COLUMN sources JSONB NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(sources) = 'array'),
    DROP CONSTRAINT mentor_turns_conversation_id_request_id_key,
    ADD CONSTRAINT mentor_turns_thread_request UNIQUE NULLS NOT DISTINCT (conversation_id, side_chat_id, request_id),
    ADD CONSTRAINT mentor_turns_thread_owner FOREIGN KEY (conversation_id, side_chat_id)
        REFERENCES mentor_side_chats (conversation_id, id) ON DELETE CASCADE;

DROP INDEX mentor_turns_history_idx;
CREATE INDEX mentor_turns_history_idx ON mentor_turns (conversation_id, side_chat_id, id DESC);
DROP INDEX mentor_turns_one_stream_idx;
CREATE UNIQUE INDEX mentor_turns_one_stream_idx ON mentor_turns (conversation_id, side_chat_id)
    NULLS NOT DISTINCT WHERE status = 'STREAMING';

CREATE FUNCTION mentor_search_vector(body TEXT) RETURNS TSVECTOR
LANGUAGE SQL IMMUTABLE PARALLEL SAFE AS $$
    SELECT to_tsvector('simple', COALESCE(string_agg(token, ' '), ''))
    FROM (
        SELECT lower(run[1]) AS token FROM regexp_matches(body, '([A-Za-z0-9]+)', 'g') AS run
        UNION ALL
        SELECT 'zz' || encode(convert_to(substring(run[1] FROM pos FOR 2), 'UTF8'), 'hex')
        FROM regexp_matches(body, '([㐀-鿿]+)', 'g') AS run
        CROSS JOIN LATERAL generate_series(1, char_length(run[1]) - 1) AS pos
    ) AS tokens
$$;

CREATE FUNCTION mentor_resume_body(content JSONB) RETURNS TEXT
LANGUAGE SQL IMMUTABLE PARALLEL SAFE RETURNS NULL ON NULL INPUT AS $$
    SELECT (content - 'layout' #- '{profile,photo}' #- '{profile,email}'
            #- '{profile,phone}' #- '{profile,links,wechat}')::text
$$;

CREATE INDEX mentor_turns_search_idx ON mentor_turns USING GIN (
    (setweight(mentor_search_vector(student_message), 'A') ||
     setweight(mentor_search_vector(mentor_reply), 'C'))
);
CREATE INDEX mentor_resume_versions_search_idx ON resume_versions USING GIN (
    (mentor_search_vector(mentor_resume_body(content)))
) WHERE content IS NOT NULL;
CREATE INDEX mentor_resume_uploads_search_idx ON resume_uploads USING GIN (
    (mentor_search_vector(extracted_text || ' ' || file_name))
);

ALTER TABLE mentor_turns ADD CONSTRAINT mentor_turns_owner UNIQUE (conversation_id, id);

CREATE TABLE mentor_memory_notes (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES mentor_main_conversations (id) ON DELETE CASCADE,
    source_turn_id BIGINT,
    category VARCHAR(16) NOT NULL CHECK (category IN ('GOAL', 'EXPERIENCE', 'PREFERENCE', 'NOTE')),
    body TEXT NOT NULL CHECK (char_length(body) BETWEEN 1 AND 1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (conversation_id, source_turn_id)
        REFERENCES mentor_turns (conversation_id, id) ON DELETE CASCADE,
    UNIQUE NULLS NOT DISTINCT (conversation_id, source_turn_id, body)
);
CREATE INDEX mentor_memory_notes_owner_idx ON mentor_memory_notes (conversation_id, id DESC);
CREATE INDEX mentor_memory_notes_search_idx ON mentor_memory_notes USING GIN ((mentor_search_vector(body)));

CREATE TABLE mentor_files (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES mentor_main_conversations (id) ON DELETE CASCADE,
    request_id UUID NOT NULL,
    file_name VARCHAR(240) NOT NULL,
    content_type TEXT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    extracted_text TEXT NOT NULL CHECK (char_length(extracted_text) <= 30000),
    file_bytes BYTEA NOT NULL CHECK (octet_length(file_bytes) BETWEEN 1 AND 5242880),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (conversation_id, request_id)
);
CREATE INDEX mentor_files_owner_idx ON mentor_files (conversation_id, id DESC);
CREATE INDEX mentor_files_search_idx ON mentor_files USING GIN ((mentor_search_vector(extracted_text || ' ' || file_name)));
