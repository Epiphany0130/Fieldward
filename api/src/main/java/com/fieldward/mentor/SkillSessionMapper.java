package com.fieldward.mentor;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
interface SkillSessionMapper {
    String COLUMNS = "assessment_id, conversation_id, side_chat_id, revision, state::text AS state";

    @Select("SELECT " + COLUMNS + " FROM career_skill_sessions WHERE account_id = #{account} AND assessment_id = #{id}")
    Stored find(@Param("account") long account, @Param("id") long id);

    @Select("SELECT " + COLUMNS + """
             FROM career_skill_sessions WHERE conversation_id = #{conversation} AND side_chat_id = #{side}
            """)
    Stored bySide(@Param("conversation") long conversation, @Param("side") long side);

    @Select("""
            SELECT session.assessment_id, session.conversation_id, session.side_chat_id, session.revision, session.state::text AS state
            FROM career_skill_sessions session
            JOIN career_targets target ON target.account_id = session.account_id AND target.assessment_id = session.assessment_id
            WHERE session.conversation_id = #{conversation}
            """)
    Stored current(long conversation);

    @Insert("""
            INSERT INTO career_skill_sessions (account_id, assessment_id, conversation_id, side_chat_id, state)
            VALUES (#{account}, #{id}, #{conversation}, #{side}, CAST(#{state} AS jsonb))
            """)
    void create(@Param("account") long account, @Param("id") long id, @Param("conversation") long conversation,
                @Param("side") long side, @Param("state") String state);

    @Select("""
            SELECT CASE WHEN lease_until <= CURRENT_TIMESTAMP THEN claim_id::text ELSE NULL END
            FROM career_skill_sessions WHERE account_id = #{account} AND assessment_id = #{id} FOR UPDATE
            """)
    String expiredClaim(@Param("account") long account, @Param("id") long id);

    @Update("""
            UPDATE career_skill_sessions
            SET claim_id = CAST(#{claim} AS uuid), lease_until = CURRENT_TIMESTAMP + interval '90 seconds'
            WHERE account_id = #{account} AND assessment_id = #{id} AND revision = #{revision}
              AND (claim_id IS NULL OR lease_until <= CURRENT_TIMESTAMP)
            """)
    int claim(@Param("account") long account, @Param("id") long id, @Param("revision") int revision,
              @Param("claim") String claim);

    @Update("""
            UPDATE career_skill_sessions SET revision = revision + 1, state = CAST(#{state} AS jsonb),
                claim_id = NULL, lease_until = NULL
            WHERE account_id = #{account} AND assessment_id = #{id} AND claim_id = CAST(#{claim} AS uuid)
              AND lease_until > CURRENT_TIMESTAMP
            """)
    int finish(@Param("account") long account, @Param("id") long id, @Param("claim") String claim,
               @Param("state") String state);

    @Update("""
            UPDATE career_skill_sessions SET claim_id = NULL, lease_until = NULL
            WHERE account_id = #{account} AND assessment_id = #{id} AND claim_id = CAST(#{claim} AS uuid)
            """)
    void release(@Param("account") long account, @Param("id") long id, @Param("claim") String claim);

    record Stored(long assessmentId, long conversationId, long sideChatId, int revision, String state) {}
}
