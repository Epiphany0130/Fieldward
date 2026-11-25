package com.fieldward.resume;

import java.time.OffsetDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
interface ResumeDraftMapper {
    String COLUMNS = "content::text AS content, revision, updated_at";

    @Select("SELECT " + COLUMNS + " FROM resume_drafts WHERE account_id = #{accountId}")
    StoredDraft find(long accountId);

    @Select(value = """
            INSERT INTO resume_drafts (account_id, content, revision)
            VALUES (#{accountId}, CAST(#{content} AS jsonb), 1)
            ON CONFLICT (account_id) DO NOTHING
            RETURNING
            """ + COLUMNS, affectData = true)
    StoredDraft insert(@Param("accountId") long accountId, @Param("content") String content);

    @Select(value = """
            UPDATE resume_drafts
            SET content = CAST(#{content} AS jsonb), revision = revision + 1, updated_at = CURRENT_TIMESTAMP
            WHERE account_id = #{accountId} AND revision = #{revision} AND content <> CAST(#{content} AS jsonb)
            RETURNING
            """ + COLUMNS, affectData = true)
    StoredDraft update(@Param("accountId") long accountId, @Param("revision") long revision,
                       @Param("content") String content);

    record StoredDraft(String content, long revision, OffsetDateTime updatedAt) {}
}
