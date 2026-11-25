package com.fieldward.mentor;

import java.time.OffsetDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MentorConversationMapper {
    @Insert("""
            INSERT INTO mentor_main_conversations (account_id, first_resume_version)
            VALUES (#{accountId}, #{firstVersion})
            """)
    void createMain(@Param("accountId") long accountId, @Param("firstVersion") long firstVersion);

    @Select("""
            SELECT CAST(conversation.id AS text) AS conversation_id, conversation.created_at,
                   latest.version AS latest_resume_version, latest.content -> 'profile' ->> 'name' AS resume_name
            FROM mentor_main_conversations conversation
            CROSS JOIN LATERAL (
                SELECT version, content FROM resume_versions
                WHERE account_id = conversation.account_id
                ORDER BY version DESC LIMIT 1
            ) latest
            WHERE conversation.account_id = #{accountId}
            """)
    Workspace findWorkspace(long accountId);

    record Workspace(String conversationId, OffsetDateTime createdAt, long latestResumeVersion, String resumeName) {}
}
