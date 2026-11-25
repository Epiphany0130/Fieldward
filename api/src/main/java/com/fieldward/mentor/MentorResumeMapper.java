package com.fieldward.mentor;

import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
interface MentorResumeMapper {
    String VERSIONS = """
            FROM mentor_main_conversations mentor
            JOIN resume_versions version ON version.account_id = mentor.account_id
            LEFT JOIN resume_uploads upload ON upload.account_id = version.account_id
                                           AND upload.request_id = version.upload_request_id
            LEFT JOIN mentor_document_ocr ocr ON ocr.account_id = version.account_id AND ocr.sha256 = upload.sha256
            """;
    String ITEM = """
            version.version, version.submitted_at, upload.file_name,
            COALESCE(upload.content_type, 'application/json') AS content_type,
            (upload.file_name IS NULL OR upload.extracted_text <> '' OR ocr.body IS NOT NULL) AS readable,
            CASE WHEN upload.content_type = 'application/pdf' AND upload.extracted_text = ''
                THEN COALESCE(ocr.status, 'PENDING') END AS ocr_status
            """;
    String DOCUMENT_BODY = ", COALESCE(NULLIF(upload.extracted_text, ''), ocr.body, mentor_resume_body(version.content), '') AS body ";

    @Select("SELECT " + ITEM + VERSIONS + """
            WHERE mentor.id = #{conversationId}
              AND version.version < COALESCE(CAST(#{before} AS bigint), 9223372036854775807)
            ORDER BY version.version DESC LIMIT #{limit}
            """)
    List<Item> catalog(@Param("conversationId") long conversationId, @Param("before") Long before,
                       @Param("limit") int limit);

    @Select("SELECT " + ITEM + DOCUMENT_BODY + VERSIONS + """
            WHERE mentor.id = #{conversationId} AND version.version = #{version}
            """)
    Document find(@Param("conversationId") long conversationId, @Param("version") long version);

    @Select("SELECT " + ITEM + DOCUMENT_BODY + VERSIONS + """
            WHERE mentor.id = #{conversationId}
              AND (mentor_search_vector(mentor_resume_body(version.content)) @@ CAST(#{query} AS tsquery)
                OR mentor_search_vector(upload.extracted_text || ' ' || upload.file_name) @@ CAST(#{query} AS tsquery)
                OR mentor_search_vector(ocr.body) @@ CAST(#{query} AS tsquery))
            ORDER BY GREATEST(
                ts_rank(mentor_search_vector(mentor_resume_body(version.content)), CAST(#{query} AS tsquery), 32),
                ts_rank(mentor_search_vector(upload.extracted_text || ' ' || upload.file_name), CAST(#{query} AS tsquery), 32),
                ts_rank(mentor_search_vector(ocr.body), CAST(#{query} AS tsquery), 32)
            ) DESC, version.version DESC LIMIT 5
            """)
    List<Document> search(@Param("conversationId") long conversationId, @Param("query") String query);

    record Item(long version, OffsetDateTime submittedAt, String fileName, String contentType, boolean readable,
                String ocrStatus) {}

    record Document(long version, OffsetDateTime submittedAt, String fileName, String contentType, boolean readable,
                    String ocrStatus, String body) {}
}
