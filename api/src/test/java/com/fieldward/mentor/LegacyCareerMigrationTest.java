package com.fieldward.mentor;

import com.fieldward.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

class LegacyCareerMigrationTest extends PostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired LegacyCareerMapper migration;
    @Autowired SkillSessions sessions;
    @Autowired LegacyCareerBackfill backfill;
    @Autowired CareerPlans plans;
    @Autowired CareerController controller;
    @Test @Transactional
    void importsConservativelyAndKeepsOriginalsPrivate() {
        long owner = account("legacy-owner"), other = account("legacy-other");
        jdbc.update("INSERT INTO resume_uploads(account_id,request_id,file_name,content_type,sha256,extracted_text,file_bytes) VALUES (?,CAST(? AS uuid),'original.pdf','application/pdf',?,'fixture',?)", owner, "00000000-0000-0000-0000-000000000001", "b".repeat(64), new byte[]{1,2,3});
        jdbc.update("INSERT INTO resume_versions(account_id,version,upload_request_id) VALUES (?,1,CAST(? AS uuid))", owner, "00000000-0000-0000-0000-000000000001");
        long conversation = jdbc.queryForObject("INSERT INTO mentor_main_conversations(account_id,first_resume_version) VALUES (?,1) RETURNING id", Long.class, owner);
        long side = jdbc.queryForObject("INSERT INTO mentor_side_chats(conversation_id,title) VALUES (?, 'old chat') RETURNING id", Long.class, conversation);
        long assessment = jdbc.queryForObject("INSERT INTO career_assessments(account_id,resume_version,direction_id,source_digest,status,attempt,report) VALUES (?,1,'java-backend',?,'READY',1,CAST(? AS jsonb)) RETURNING id", Long.class, owner, "a".repeat(64), "{\"skills\":[{\"skillId\":\"sql\"}]}");
        String state = "{\"phase\":\"REPORT\",\"topics\":[\"关系数据库与 SQL\",\"unknown topic\"],\"selfMarks\":{\"关系数据库与 SQL\":\"GREEN\",\"unknown topic\":\"GREEN\"},\"testTopics\":[\"关系数据库与 SQL\"],\"grades\":[{\"topic\":\"关系数据库与 SQL\",\"mark\":\"GREEN\",\"score\":90,\"feedback\":\"ok\"}],\"studyTopics\":[],\"lessonIndex\":0}";
        jdbc.update("INSERT INTO career_skill_sessions(account_id,assessment_id,conversation_id,side_chat_id,state) VALUES (?,?,?,?,CAST(? AS jsonb))", owner, assessment, conversation, side, state);
        backfill.run(null);
        var result = migration.importAssessment(owner, assessment);
        assertThat(result.imported()).isEqualTo(3);
        assertThat(result.unmapped()).isEqualTo(1);
        assertThat(migration.importAssessment(owner, assessment)).isEqualTo(result);
        backfill.run(null);
        assertThat(jdbc.queryForObject("SELECT migration_version FROM legacy_capability_migrations WHERE account_id=? AND assessment_id=?", Integer.class, owner, assessment)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT report->'skills'->0->>'skillId' FROM career_assessments WHERE id=?", String.class, assessment)).isEqualTo("sql");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM capability_evidence WHERE account_id=? AND capability_id='sql' AND outcome='PENDING'", Integer.class, owner)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM legacy_capability_unmapped WHERE account_id=? AND outcome='PENDING'", Integer.class, owner)).isEqualTo(1);
        assertThatThrownBy(() -> migration.importAssessment(other, assessment)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(sessions.get(owner, assessment).sideId()).isEqualTo(Long.toString(side));
        assertThat(sessions.get(other, assessment)).isNull();
        assertThat(jdbc.queryForObject("SELECT file_bytes FROM resume_uploads WHERE account_id=?", byte[].class, owner)).containsExactly(1,2,3);
    }
    @Test @Transactional
    void normalReadsImportNewReportsAndUpdatedSessions() {
        long owner = account("legacy-updates"), other = account("legacy-updates-other");
        jdbc.update("INSERT INTO resume_versions(account_id,version,draft_revision,content) VALUES (?,1,1,'{}')", owner);
        long conversation = jdbc.queryForObject("INSERT INTO mentor_main_conversations(account_id,first_resume_version) VALUES (?,1) RETURNING id", Long.class, owner);
        long side = jdbc.queryForObject("INSERT INTO mentor_side_chats(conversation_id,title) VALUES (?, 'chat') RETURNING id", Long.class, conversation);
        long assessment = jdbc.queryForObject("INSERT INTO career_assessments(account_id,resume_version,direction_id,source_digest,status,attempt) VALUES (?,1,'java-backend',?,'WAITING_TEXT',1) RETURNING id", Long.class, owner, "c".repeat(64));
        migration.importAssessment(owner, assessment);
        jdbc.update("UPDATE career_assessments SET status='READY', report=CAST(? AS jsonb) WHERE id=?", "{\"interviewThreshold\":0,\"coreCovered\":false,\"skills\":[{\"skillId\":\"sql\",\"weight\":1,\"core\":false}]}", assessment);
        var ownerPrincipal = org.mockito.Mockito.mock(com.fieldward.account.AccountPrincipal.class);
        var otherPrincipal = org.mockito.Mockito.mock(com.fieldward.account.AccountPrincipal.class);
        org.mockito.Mockito.when(ownerPrincipal.accountId()).thenReturn(owner);
        org.mockito.Mockito.when(otherPrincipal.accountId()).thenReturn(other);
        assertThatThrownBy(() -> controller.archived(otherPrincipal, assessment)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(controller.archived(ownerPrincipal, assessment).id()).isEqualTo(Long.toString(assessment));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM capability_evidence WHERE account_id=?", Integer.class, owner)).isEqualTo(1);
        String state = "{\"phase\":\"REPORT\",\"topics\":[\"关系数据库与 SQL\"],\"selfMarks\":{\"关系数据库与 SQL\":\"GREEN\"},\"testTopics\":[],\"grades\":[],\"studyTopics\":[],\"lessonIndex\":0}";
        jdbc.update("INSERT INTO career_skill_sessions(account_id,assessment_id,conversation_id,side_chat_id,state) VALUES (?,?,?,?,CAST(? AS jsonb))", owner, assessment, conversation, side, state);
        assertThat(sessions.get(other, assessment)).isNull();
        sessions.get(owner, assessment);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM capability_evidence WHERE account_id=?", Integer.class, owner)).isEqualTo(2);
        jdbc.update("UPDATE career_skill_sessions SET revision=revision+1, state=jsonb_set(state,'{grades}',CAST(? AS jsonb)) WHERE assessment_id=?", "[{\"topic\":\"关系数据库与 SQL\",\"mark\":\"GREEN\",\"score\":90,\"feedback\":\"ok\"}]", assessment);
        sessions.get(owner, assessment);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM capability_evidence WHERE account_id=?", Integer.class, owner)).isEqualTo(3);
    }

    @Test @Transactional
    void backfillRetriesWhenAReportBecomesReady() {
        long owner = account("legacy-report-retry");
        jdbc.update("INSERT INTO resume_versions(account_id,version,draft_revision,content) VALUES (?,1,1,'{}')", owner);
        long id = jdbc.queryForObject("INSERT INTO career_assessments(account_id,resume_version,direction_id,source_digest,status,attempt) VALUES (?,1,'java-backend',?,'WAITING_TEXT',1) RETURNING id", Long.class, owner, "e".repeat(64));
        backfill.run(null);
        jdbc.update("UPDATE career_assessments SET status='READY', report=CAST(? AS jsonb) WHERE id=?", "{\"skills\":[{\"skillId\":\"sql\"}]}", id);
        backfill.run(null);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM capability_evidence WHERE account_id=?", Integer.class, owner)).isEqualTo(1);
    }

    @Test
    void failedBackfillItemDoesNotPreventFollowingItems() {
        var db = org.mockito.Mockito.mock(JdbcTemplate.class);
        var importer = org.mockito.Mockito.mock(LegacyCareerMapper.class);
        org.mockito.Mockito.when(db.query(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<long[]>>any()))
                .thenReturn(java.util.List.of(new long[]{1, 10}, new long[]{2, 20}));
        org.mockito.Mockito.when(importer.importAssessment(1, 10)).thenThrow(new IllegalStateException("bad historical data"));
        new LegacyCareerBackfill(db, importer).run(null);
        org.mockito.Mockito.verify(importer).importAssessment(2, 20);
    }
    @Test
    void migratesRecordsActuallyCreatedBeforeV12() throws Exception {
        try (var postgres = io.zonky.test.db.postgres.embedded.EmbeddedPostgres.start()) {
            var ds = postgres.getPostgresDatabase();
            org.flywaydb.core.Flyway.configure().dataSource(ds).baselineVersion("1").load().baseline();
            org.flywaydb.core.Flyway.configure().dataSource(ds).target("11").load().migrate();
            var db = new JdbcTemplate(ds);
            long owner = db.queryForObject("INSERT INTO accounts(email,display_name,password_hash,role) VALUES ('pre12@test.invalid','pre12','fixture','STUDENT') RETURNING id", Long.class);
            String request = "10000000-0000-0000-0000-000000000001";
            db.update("INSERT INTO resume_uploads(account_id,request_id,file_name,content_type,sha256,extracted_text,file_bytes) VALUES (?,CAST(? AS uuid),'pre12.pdf','application/pdf',?,'synthetic',?)", owner, request, "f".repeat(64), new byte[]{5,6,7});
            db.update("INSERT INTO resume_versions(account_id,version,upload_request_id) VALUES (?,1,CAST(? AS uuid))", owner, request);
            long conversation = db.queryForObject("INSERT INTO mentor_main_conversations(account_id,first_resume_version) VALUES (?,1) RETURNING id", Long.class, owner);
            long side = db.queryForObject("INSERT INTO mentor_side_chats(conversation_id,title) VALUES (?,'legacy side chat') RETURNING id", Long.class, conversation);
            db.update("INSERT INTO mentor_turns(conversation_id,side_chat_id,request_id,student_message,mentor_reply,status,attempt) VALUES (?,?,CAST(? AS uuid),'synthetic question','synthetic answer','COMPLETED',1)", conversation, side, "10000000-0000-0000-0000-000000000002");
            long id = db.queryForObject("INSERT INTO career_assessments(account_id,resume_version,direction_id,source_digest,status,attempt,report) VALUES (?,1,'java-backend',?,'READY',1,CAST(? AS jsonb)) RETURNING id", Long.class, owner, "d".repeat(64), "{\"skills\":[{\"skillId\":\"sql\"}]}");
            String state = "{\"phase\":\"REPORT\",\"topics\":[\"关系数据库与 SQL\",\"unknown topic\"],\"selfMarks\":{\"关系数据库与 SQL\":\"GREEN\",\"unknown topic\":\"GREEN\"},\"testTopics\":[\"关系数据库与 SQL\"],\"grades\":[{\"topic\":\"关系数据库与 SQL\",\"mark\":\"GREEN\",\"score\":90,\"feedback\":\"ok\"}],\"studyTopics\":[],\"lessonIndex\":0}";
            db.update("INSERT INTO career_skill_sessions(account_id,assessment_id,conversation_id,side_chat_id,state) VALUES (?,?,?,?,CAST(? AS jsonb))", owner, id, conversation, side, state);
            long other = db.queryForObject("INSERT INTO accounts(email,display_name,password_hash,role) VALUES ('pre12-other@test.invalid','other','fixture','STUDENT') RETURNING id", Long.class);
            org.flywaydb.core.Flyway.configure().dataSource(ds).load().migrate();
            var importer = new LegacyCareerMapper(db, tools.jackson.databind.json.JsonMapper.builder().build());
            var result = importer.importAssessment(owner, id);
            assertThat(result.imported()).isEqualTo(3);
            assertThat(result.unmapped()).isEqualTo(1);
            assertThat(importer.importAssessment(owner, id)).isEqualTo(result);
            assertThatThrownBy(() -> importer.importAssessment(other, id)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
            assertThat(db.queryForObject("SELECT count(*) FROM capability_evidence WHERE account_id=? AND outcome='PENDING'", Integer.class, owner)).isEqualTo(3);
            assertThat(db.queryForObject("SELECT count(*) FROM legacy_capability_unmapped WHERE account_id=? AND outcome='PENDING'", Integer.class, owner)).isEqualTo(1);
            assertThat(db.queryForObject("SELECT report->'skills'->0->>'skillId' FROM career_assessments WHERE id=?", String.class, id)).isEqualTo("sql");
            assertThat(db.queryForObject("SELECT file_bytes FROM resume_uploads WHERE account_id=?", byte[].class, owner)).containsExactly(5,6,7);
            assertThat(db.queryForObject("SELECT mentor_reply FROM mentor_turns WHERE conversation_id=? AND side_chat_id=?", String.class, conversation, side)).isEqualTo("synthetic answer");
            assertThat(db.queryForObject("SELECT state->'grades'->0->>'score' FROM career_skill_sessions WHERE account_id=? AND assessment_id=?", String.class, owner, id)).isEqualTo("90");
        }
    }
    @Test @Transactional
    void sessionStartAndStepImportWithoutARead() {
        long owner = account("legacy-session-write");
        jdbc.update("INSERT INTO resume_versions(account_id,version,draft_revision,content) VALUES (?,1,1,'{}')", owner);
        jdbc.update("INSERT INTO mentor_main_conversations(account_id,first_resume_version) VALUES (?,1)", owner);
        String report = "{\"interviewThreshold\":0,\"coreCovered\":false,\"skills\":[],\"route\":{\"sourceUrl\":\"fixture\",\"phases\":[{\"title\":\"fixture\",\"topics\":[\"关系数据库与 SQL\"]}]}}";
        long id = jdbc.queryForObject("INSERT INTO career_assessments(account_id,resume_version,direction_id,source_digest,status,attempt,report) VALUES (?,1,'java-backend',?,'READY',1,CAST(? AS jsonb)) RETURNING id", Long.class, owner, "1".repeat(64), report);
        var started = sessions.start(owner, id, new java.util.HashMap<>(java.util.Map.of("关系数据库与 SQL", CareerFit.Mark.RED)));
        assertThat(started.phase()).isEqualTo(SkillSessions.Phase.REPORT);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM capability_evidence WHERE account_id=? AND outcome='PENDING'", Integer.class, owner)).isEqualTo(1);
        var next = sessions.step(owner, id, started.revision(), "");
        assertThat(next.phase()).isEqualTo(SkillSessions.Phase.LEARNING);
        assertThat(jdbc.queryForObject("SELECT session_revision FROM legacy_capability_migrations WHERE account_id=? AND assessment_id=?", Integer.class, owner, id)).isEqualTo(next.revision());
        assertThat(sessions.step(owner, id, started.revision(), "").revision()).isEqualTo(next.revision());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM capability_evidence WHERE account_id=?", Integer.class, owner)).isEqualTo(1);
    }

    @Test
    void completedAnalysisImportsWithoutARead() throws Exception {
        long owner = account("legacy-analysis-write");
        try {
            jdbc.update("INSERT INTO resume_uploads(account_id,request_id,file_name,content_type,sha256,extracted_text,file_bytes) VALUES (?,CAST(? AS uuid),'empty.pdf','application/pdf',?,'',?)", owner, "20000000-0000-0000-0000-000000000001", "2".repeat(64), new byte[]{1});
            jdbc.update("INSERT INTO resume_versions(account_id,version,upload_request_id) VALUES (?,1,CAST(? AS uuid))", owner, "20000000-0000-0000-0000-000000000001");
            jdbc.update("INSERT INTO mentor_main_conversations(account_id,first_resume_version) VALUES (?,1)", owner);
            long id = Long.parseLong(plans.start(owner, 1, "java-backend").id());
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            int count = 0;
            while (System.nanoTime() < deadline && count == 0) {
                count = jdbc.queryForObject("SELECT count(*) FROM capability_evidence WHERE account_id=? AND source_type='LEGACY_ASSESSMENT'", Integer.class, owner);
                if (count == 0) Thread.sleep(50);
            }
            assertThat(jdbc.queryForObject("SELECT status FROM career_assessments WHERE id=?", String.class, id)).isEqualTo("READY");
            assertThat(count).isEqualTo(5);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM capability_evidence WHERE account_id=? AND outcome <> 'PENDING'", Integer.class, owner)).isZero();
        } finally {
            jdbc.update("DELETE FROM accounts WHERE id=?", owner);
        }
    }

    private long account(String name) { return jdbc.queryForObject("INSERT INTO accounts(email,display_name,password_hash,role) VALUES (?,?, 'fixture','STUDENT') RETURNING id", Long.class, name+"@test.invalid", name); }
}
