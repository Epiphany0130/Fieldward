package com.fieldward.mentor;

import java.time.OffsetDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
interface CareerMapper {
    String ROW = """
            assessment.id, assessment.account_id, assessment.resume_version, assessment.direction_id,
            CASE WHEN assessment.status = 'ANALYZING' AND assessment.lease_until <= CURRENT_TIMESTAMP
                 THEN 'FAILED' ELSE assessment.status END AS status,
            assessment.attempt, assessment.report::text AS report, assessment.updated_at
            """;
    String CURRENT = """
            FROM career_targets target
            JOIN career_assessments assessment ON assessment.id = target.assessment_id AND assessment.account_id = target.account_id
            """;

    @Select("SELECT id FROM accounts WHERE id = #{accountId} FOR NO KEY UPDATE")
    Long lockAccount(long accountId);

    @Select("SELECT " + ROW + CURRENT + " WHERE target.account_id = #{accountId}")
    Assessment current(long accountId);

    @Select("SELECT " + ROW + """
            FROM career_assessments assessment WHERE assessment.account_id = #{accountId} AND assessment.id = #{id}
            """)
    Assessment find(@Param("accountId") long accountId, @Param("id") long id);

    @Select("SELECT " + ROW + CURRENT + """
            JOIN mentor_main_conversations mentor ON mentor.account_id = target.account_id
            WHERE mentor.id = #{conversation}
            """)
    Assessment currentForConversation(long conversation);

    @Select("""
            SELECT mentor.career_onboarding_required AND NOT EXISTS (
                SELECT 1
                """ + CURRENT + """
                WHERE target.account_id = mentor.account_id AND assessment.status = 'READY'
            )
            FROM mentor_main_conversations mentor WHERE mentor.id = #{conversation}
            """)
    boolean needsOnboarding(long conversation);

    @Update("""
            UPDATE mentor_main_conversations SET career_onboarding_required = FALSE
            WHERE account_id = #{accountId} AND career_onboarding_required
            """)
    void finishOnboarding(long accountId);

    @Select(value = """
            INSERT INTO career_assessments AS assessment
                (account_id, resume_version, direction_id, source_digest, status, attempt, lease_until)
            VALUES (#{accountId}, #{resumeVersion}, #{directionId}, #{digest}, #{status}, 1,
                CASE WHEN #{status} = 'ANALYZING' THEN CURRENT_TIMESTAMP + INTERVAL '2 minutes' END)
            ON CONFLICT (account_id, resume_version, direction_id, source_digest)
            DO UPDATE SET status = EXCLUDED.status, attempt = assessment.attempt + 1, lease_until = EXCLUDED.lease_until,
                updated_at = CURRENT_TIMESTAMP
            WHERE assessment.status = 'FAILED'
                OR (assessment.status = 'ANALYZING' AND assessment.lease_until <= CURRENT_TIMESTAMP)
            RETURNING
            """ + ROW, affectData = true)
    Assessment claim(@Param("accountId") long accountId, @Param("resumeVersion") long resumeVersion,
                     @Param("directionId") String directionId, @Param("digest") String digest, @Param("status") String status);

    @Select("SELECT " + ROW + """
            FROM career_assessments assessment WHERE assessment.account_id = #{accountId}
                AND assessment.resume_version = #{resumeVersion} AND assessment.direction_id = #{directionId}
                AND assessment.source_digest = #{digest}
            """)
    Assessment existing(@Param("accountId") long accountId, @Param("resumeVersion") long resumeVersion,
                        @Param("directionId") String directionId, @Param("digest") String digest);

    @Insert("""
            INSERT INTO career_targets (account_id, assessment_id) VALUES (#{accountId}, #{assessmentId})
            ON CONFLICT (account_id) DO UPDATE SET assessment_id = EXCLUDED.assessment_id
            """)
    void activate(@Param("accountId") long accountId, @Param("assessmentId") long assessmentId);

    @Update("""
            UPDATE career_assessments SET report = CAST(#{report} AS jsonb), status = #{status},
                lease_until = NULL, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{assessment.id} AND account_id = #{assessment.accountId} AND attempt = #{assessment.attempt}
                AND status = 'ANALYZING' AND lease_until > CURRENT_TIMESTAMP
            """)
    int settle(@Param("assessment") Assessment assessment, @Param("status") String status, @Param("report") String report);

    enum Status { WAITING_TEXT, ANALYZING, READY, FAILED }
    record Assessment(long id, long accountId, long resumeVersion, String directionId, Status status,
                      int attempt, String report, OffsetDateTime updatedAt) {}
}
