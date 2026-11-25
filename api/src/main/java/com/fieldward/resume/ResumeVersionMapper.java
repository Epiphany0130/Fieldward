package com.fieldward.resume;

import java.time.OffsetDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
interface ResumeVersionMapper {
    String COLUMNS = "version, draft_revision, submitted_at";

    @Select("SELECT " + COLUMNS + " FROM resume_versions WHERE account_id = #{accountId} ORDER BY version DESC LIMIT 1")
    Submission latest(long accountId);

    @Select("""
            SELECT version, submitted_at, content::text AS content FROM resume_versions
            WHERE account_id = #{accountId} ORDER BY version DESC LIMIT 1
            """)
    StoredVersion latestDocument(long accountId);

    @Select("""
            SELECT version, submitted_at, content::text AS content FROM resume_versions
            WHERE account_id = #{accountId} AND version = #{version}
            """)
    StoredVersion findDocument(@Param("accountId") long accountId, @Param("version") long version);

    @Select("SELECT " + COLUMNS + " FROM resume_versions WHERE account_id = #{accountId} AND draft_revision = #{revision}")
    Submission findByDraftRevision(@Param("accountId") long accountId, @Param("revision") long revision);

    @Select(value = """
            INSERT INTO resume_versions (account_id, version, draft_revision, content)
            SELECT #{accountId}, COALESCE(MAX(version), 0) + 1, #{revision}, CAST(#{content} AS jsonb)
            FROM resume_versions WHERE account_id = #{accountId}
            RETURNING
            """ + COLUMNS, affectData = true)
    Submission insert(@Param("accountId") long accountId, @Param("revision") long revision,
                      @Param("content") String content);

    record Submission(long version, long draftRevision, OffsetDateTime submittedAt) {}

    record StoredVersion(long version, OffsetDateTime submittedAt, String content) {}
}
