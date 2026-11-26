CREATE TABLE career_skill_sessions (
    assessment_id BIGINT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    conversation_id BIGINT NOT NULL,
    side_chat_id BIGINT NOT NULL,
    revision INTEGER NOT NULL DEFAULT 0 CHECK (revision >= 0),
    state JSONB NOT NULL,
    claim_id UUID,
    lease_until TIMESTAMPTZ,
    UNIQUE (conversation_id, side_chat_id),
    FOREIGN KEY (account_id, assessment_id)
        REFERENCES career_assessments (account_id, id) ON DELETE CASCADE,
    FOREIGN KEY (conversation_id, side_chat_id)
        REFERENCES mentor_side_chats (conversation_id, id) ON DELETE CASCADE,
    CHECK ((claim_id IS NULL) = (lease_until IS NULL))
);

ALTER TABLE mentor_turns DROP CONSTRAINT mentor_turns_assistant_opening;
ALTER TABLE mentor_turns ADD CONSTRAINT mentor_turns_assistant_opening
    CHECK (student_message IS NOT NULL OR status = 'COMPLETED');
