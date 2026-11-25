CREATE TABLE mentor_turns (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES mentor_main_conversations (id) ON DELETE CASCADE,
    request_id UUID NOT NULL,
    student_message TEXT NOT NULL CHECK (char_length(student_message) BETWEEN 1 AND 4000),
    mentor_reply TEXT CHECK (char_length(mentor_reply) BETWEEN 1 AND 12000),
    status VARCHAR(16) NOT NULL CHECK (status IN ('STREAMING', 'COMPLETED', 'INTERRUPTED')),
    attempt INTEGER NOT NULL CHECK (attempt > 0),
    lease_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (conversation_id, request_id),
    CHECK (
        (status = 'STREAMING' AND lease_until IS NOT NULL AND mentor_reply IS NULL)
        OR (status = 'COMPLETED' AND lease_until IS NULL AND mentor_reply IS NOT NULL)
        OR (status = 'INTERRUPTED' AND lease_until IS NULL AND mentor_reply IS NULL)
    )
);

CREATE INDEX mentor_turns_history_idx ON mentor_turns (conversation_id, id DESC);
CREATE UNIQUE INDEX mentor_turns_one_stream_idx ON mentor_turns (conversation_id) WHERE status = 'STREAMING';
