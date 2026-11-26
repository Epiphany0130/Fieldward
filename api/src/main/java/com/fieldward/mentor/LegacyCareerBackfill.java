package com.fieldward.mentor;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Resume interrupted backfills on startup; each assessment commits independently. */
@Component
class LegacyCareerBackfill implements ApplicationRunner {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LegacyCareerBackfill.class);
    private final JdbcTemplate jdbc;
    private final LegacyCareerMapper migration;
    LegacyCareerBackfill(JdbcTemplate jdbc, LegacyCareerMapper migration) {
        this.jdbc = jdbc;
        this.migration = migration;
    }
    @Override public void run(ApplicationArguments args) {
        jdbc.query("""
                SELECT a.account_id, a.id FROM career_assessments a
                LEFT JOIN legacy_capability_migrations m ON m.account_id=a.account_id AND m.assessment_id=a.id
                LEFT JOIN career_skill_sessions s ON s.account_id=a.account_id AND s.assessment_id=a.id
                WHERE m.assessment_id IS NULL OR m.migration_version < 1 OR m.session_revision < COALESCE(s.revision,-1)
                    OR m.report_digest IS DISTINCT FROM md5(a.report::text)
                ORDER BY a.id
                """, (rs, rowNum) -> {
            // Materialize IDs before opening per-assessment transactions.
            return new long[]{rs.getLong(1), rs.getLong(2)};
        }).forEach(ids -> tryImport(ids[0], ids[1]));
    }
    void tryImport(long accountId, long assessmentId) {
        try {
            migration.importAssessment(accountId, assessmentId);
        } catch (RuntimeException error) {
            // Avoid logging historical content, which may contain personal data.
            log.warn("Legacy migration failed for assessment {} ({})", assessmentId, error.getClass().getSimpleName());
        }
    }
}
