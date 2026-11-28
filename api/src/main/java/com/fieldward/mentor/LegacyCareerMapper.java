package com.fieldward.mentor;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Imports old conclusions as pending evidence; their original records remain authoritative. */
@Service
class LegacyCareerMapper {
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    LegacyCareerMapper(JdbcTemplate jdbc, JsonMapper json) { this.jdbc = jdbc; this.json = json; }

    @Transactional
    public MigrationResult importAssessment(long accountId, long assessmentId) {
        var rows = jdbc.queryForList("""
                SELECT direction_id, report::text AS report, md5(report::text) AS report_digest FROM career_assessments
                WHERE account_id=? AND id=? FOR UPDATE
                """, accountId, assessmentId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Assessment not found");
        var row = rows.getFirst();
        String direction = (String) row.get("direction_id");
        String prefix = assessmentId + ":";
        boolean stableIds = false;
        if (row.get("report") != null) {
            JsonNode report = json.readTree((String) row.get("report"));
            stableIds = report.path("route").path("labels").isObject();
            int index = 0;
            for (JsonNode skill : report.path("skills")) {
                String key = skill.path("skillId").asString("");
                add(accountId, assessmentId, direction, "LEGACY_ASSESSMENT", prefix + "skill:" + index++, key, stableIds);
            }
        }
        final boolean usesStableIds = stableIds;
        var session = jdbc.queryForList("SELECT revision, state::text AS state FROM career_skill_sessions WHERE account_id=? AND assessment_id=?", accountId, assessmentId);
        int revision = -1;
        if (!session.isEmpty()) {
            revision = (Integer) session.getFirst().get("revision");
            JsonNode state = json.readTree((String) session.getFirst().get("state"));
            state.path("selfMarks").properties().forEach(entry -> add(accountId, assessmentId, direction,
                    "LEGACY_ASSESSMENT", prefix + "self:" + entry.getKey(), entry.getKey(), usesStableIds));
            int index = 0;
            for (JsonNode grade : state.path("grades")) {
                add(accountId, assessmentId, direction, "LEGACY_VERIFICATION", prefix + "grade:" + index++, grade.path("topic").asString(""), usesStableIds);
            }
        }
        jdbc.update("""
                INSERT INTO legacy_capability_migrations(account_id, assessment_id, migration_version, session_revision, report_digest)
                VALUES (?,?,1,?,?) ON CONFLICT (account_id, assessment_id) DO UPDATE
                SET migration_version=EXCLUDED.migration_version, session_revision=EXCLUDED.session_revision,
                    report_digest=EXCLUDED.report_digest,
                    migrated_at=CURRENT_TIMESTAMP
                """, accountId, assessmentId, revision, row.get("report_digest"));
        int imported = jdbc.queryForObject("SELECT count(*) FROM capability_evidence WHERE account_id=? AND source_type IN ('LEGACY_ASSESSMENT','LEGACY_VERIFICATION') AND left(source_id, length(?))=?", Integer.class, accountId, prefix, prefix);
        int unmapped = jdbc.queryForObject("SELECT count(*) FROM legacy_capability_unmapped WHERE account_id=? AND assessment_id=?", Integer.class, accountId, assessmentId);
        return new MigrationResult(imported, unmapped);
    }

    private void add(long account, long assessment, String direction, String type, String source, String key, boolean stableIds) {
        // Only the frozen initial route is eligible: later catalog edits cannot change historical meaning.
        List<String> matches = stableIds ? jdbc.queryForList("SELECT DISTINCT capability_id FROM career_route_items WHERE direction_id=? AND capability_id=?", String.class, direction, key) : jdbc.queryForList("""
                SELECT DISTINCT capability_id FROM career_route_items
                WHERE direction_id=? AND version=1 AND (legacy_skill_id=? OR title=?)
                """, String.class, direction, key, key);
        if (matches.size() == 1) {
            jdbc.update("""
                    INSERT INTO capability_evidence(account_id, capability_id, source_type, source_id, outcome)
                    VALUES (?,?,?,?,'PENDING') ON CONFLICT DO NOTHING
                    """, account, matches.getFirst(), type, source);
        } else {
            jdbc.update("""
                    INSERT INTO legacy_capability_unmapped(account_id, assessment_id, source_type, source_id, legacy_key)
                    VALUES (?,?,?,?,?) ON CONFLICT DO NOTHING
                    """, account, assessment, type, source, key);
        }
    }
    record MigrationResult(int imported, int unmapped) {}
}
