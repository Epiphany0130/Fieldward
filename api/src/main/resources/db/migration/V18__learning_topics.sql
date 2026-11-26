CREATE TABLE learning_topic_chats (
    conversation_id BIGINT NOT NULL REFERENCES mentor_main_conversations(id) ON DELETE CASCADE,
    capability_id TEXT NOT NULL REFERENCES career_capabilities(id),
    side_chat_id BIGINT NOT NULL,
    FOREIGN KEY (conversation_id, side_chat_id) REFERENCES mentor_side_chats(conversation_id, id) ON DELETE CASCADE,
    PRIMARY KEY (conversation_id, capability_id),
    UNIQUE (side_chat_id)
);
