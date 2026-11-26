package com.fieldward.mentor;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class LearningService {
    private final LearningMapper learning;
    private final MentorTurnMapper turns;

    LearningService(LearningMapper learning, MentorTurnMapper turns) {
        this.learning = learning;
        this.turns = turns;
    }

    @Transactional
    public MentorTurnMapper.Side openTopic(long accountId, String capabilityId) {
        String title = learning.capabilityTitle(capabilityId);
        if (title == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到该知识点");
        Long conversation = turns.lockConversation(accountId);
        if (conversation == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "创建第一份简历后即可开启学习");
        var existing = learning.topic(conversation, capabilityId);
        if (existing != null) return existing;
        long side = learning.createSide(conversation, title);
        learning.link(conversation, capabilityId, side);
        turns.logSideTurn(conversation, side, null, "我们来学习「" + title + "」。你目前对这个知识点了解多少，或者想从哪里开始？");
        return learning.topic(conversation, capabilityId);
    }
}

@Mapper
interface LearningMapper {
    @Select("SELECT title FROM career_capabilities WHERE id = #{capabilityId}")
    String capabilityTitle(String capabilityId);

    @Select("""
            SELECT side.id::text AS id, side.title, NULL::text AS verification_id
            FROM learning_topic_chats topic JOIN mentor_side_chats side ON side.id = topic.side_chat_id
            WHERE topic.conversation_id = #{conversation} AND topic.capability_id = #{capabilityId}
            """)
    MentorTurnMapper.Side topic(long conversation, String capabilityId);

    @Select("""
            SELECT capability.title, string_agg(DISTINCT item.objective, '；') AS objectives
            FROM learning_topic_chats topic
            JOIN career_capabilities capability ON capability.id = topic.capability_id
            LEFT JOIN career_route_items item ON item.capability_id = capability.id
                AND item.version = (SELECT max(v.version) FROM career_route_versions v WHERE v.direction_id = item.direction_id)
            WHERE topic.conversation_id = #{conversation} AND topic.side_chat_id = #{side}
            GROUP BY capability.title
            """)
    TopicContext context(long conversation, Long side);

    record TopicContext(String title, String objectives) {}

    @Select(value = """
            WITH next_id AS (SELECT nextval(pg_get_serial_sequence('mentor_side_chats', 'id')) AS id)
            INSERT INTO mentor_side_chats (id, conversation_id, title) OVERRIDING SYSTEM VALUE
            SELECT id, #{conversation}, '学习：' || #{title} || ' · ' || id FROM next_id
            RETURNING id
            """, affectData = true)
    long createSide(long conversation, String title);

    @Insert("""
            INSERT INTO learning_topic_chats (conversation_id, capability_id, side_chat_id)
            VALUES (#{conversation}, #{capabilityId}, #{side})
            """)
    void link(long conversation, String capabilityId, long side);
}
