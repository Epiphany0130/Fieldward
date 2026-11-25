package com.fieldward.resume;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
interface ResumeUploadMapper {
    @Insert("""
            INSERT INTO resume_uploads (account_id, request_id, file_name, content_type, sha256, extracted_text, file_bytes)
            VALUES (#{accountId}, CAST(#{requestId} AS uuid), #{file.name}, #{file.contentType}, #{file.sha256},
                    #{file.text}, #{file.bytes})
            """)
    void insert(@Param("accountId") long accountId, @Param("requestId") String requestId,
                @Param("file") ResumeUpload file);

    @Select("""
            SELECT sha256 FROM resume_uploads
            WHERE account_id = #{accountId} AND request_id = CAST(#{requestId} AS uuid)
            """)
    String findHash(@Param("accountId") long accountId, @Param("requestId") String requestId);

    @Select("""
            SELECT upload.file_name AS name, upload.content_type, octet_length(upload.file_bytes) AS size_bytes,
                COALESCE(NULLIF(upload.extracted_text, ''), ocr.body, '') AS text,
                CASE WHEN upload.content_type = 'application/pdf' AND upload.extracted_text = ''
                    THEN COALESCE(ocr.status, 'PENDING') END AS ocr_status
            FROM resume_uploads upload
            LEFT JOIN mentor_document_ocr ocr ON ocr.account_id = upload.account_id AND ocr.sha256 = upload.sha256
            WHERE upload.account_id = #{accountId} AND upload.request_id = CAST(#{requestId} AS uuid)
            """)
    Metadata findMetadata(@Param("accountId") long accountId, @Param("requestId") String requestId);

    @Select("""
            SELECT upload.file_name AS name, upload.content_type, upload.file_bytes AS bytes
            FROM resume_versions version
            JOIN resume_uploads upload ON upload.account_id = version.account_id
                                      AND upload.request_id = version.upload_request_id
            WHERE version.account_id = #{accountId} AND version.version = #{version}
            """)
    Original findOriginal(@Param("accountId") long accountId, @Param("version") long version);

    record Metadata(String name, String contentType, int sizeBytes, String text, String ocrStatus) {}

    record Original(String name, String contentType, byte[] bytes) {}
}
