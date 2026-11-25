package com.fieldward.resume;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
interface ResumeOcrMapper {
    @Select(value = """
            WITH expired AS (
                UPDATE mentor_document_ocr SET status = 'FAILED'
                WHERE status = 'PROCESSING' AND attempt = 3 AND available_at <= CURRENT_TIMESTAMP
            ), candidate AS (
                SELECT file.account_id, file.sha256 FROM mentor_pdf_scans file
                LEFT JOIN mentor_document_ocr result ON result.account_id = file.account_id AND result.sha256 = file.sha256
                WHERE result.account_id IS NULL
                    OR (result.status IN ('PROCESSING', 'RETRY') AND result.attempt < 3
                        AND result.available_at <= CURRENT_TIMESTAMP)
                ORDER BY COALESCE(result.available_at, CURRENT_TIMESTAMP), file.account_id, file.sha256
                LIMIT 1
            )
            INSERT INTO mentor_document_ocr AS result (account_id, sha256, status, attempt, available_at)
            SELECT account_id, sha256, 'PROCESSING', 1, CURRENT_TIMESTAMP + INTERVAL '3 minutes' FROM candidate
            ON CONFLICT (account_id, sha256) DO UPDATE
                SET status = 'PROCESSING', attempt = result.attempt + 1, available_at = EXCLUDED.available_at
                WHERE result.status IN ('PROCESSING', 'RETRY') AND result.attempt < 3
                    AND result.available_at <= CURRENT_TIMESTAMP
            RETURNING account_id, sha256, attempt
            """, affectData = true)
    Job claim();

    @Select("""
            SELECT file_bytes AS bytes FROM mentor_pdf_scans
            WHERE account_id = #{job.accountId} AND sha256 = #{job.sha256}
            LIMIT 1
            """)
    Input input(@Param("job") Job job);

    @Update("""
            UPDATE mentor_document_ocr SET body = #{text}, status = #{status},
                available_at = CURRENT_TIMESTAMP + INTERVAL '1 minute'
            WHERE account_id = #{job.accountId} AND sha256 = #{job.sha256}
                AND attempt = #{job.attempt} AND status = 'PROCESSING'
            """)
    void settle(@Param("job") Job job, @Param("status") String status, @Param("text") String text);

    record Job(long accountId, String sha256, int attempt) {}
    record Input(byte[] bytes) {}
}
