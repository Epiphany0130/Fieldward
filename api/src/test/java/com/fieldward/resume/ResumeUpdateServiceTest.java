package com.fieldward.resume;

import com.fieldward.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

class ResumeUpdateServiceTest extends PostgresIntegrationTest {
    @Autowired JdbcTemplate db;
    @Autowired ResumeUpdateService service;

    @Test @Transactional
    void passedLearningAddsSkillToLatestVersionWithoutInventingProjectAndIsIdempotent() {
        long owner = account("resume-update"), other = account("resume-update-other");
        String content = "{\"profile\":{\"name\":\"A\",\"email\":\"\",\"phone\":\"\"},\"education\":{\"school\":\"School\",\"major\":\"\",\"degree\":\"\",\"graduation\":\"\"},\"experiences\":[],\"skills\":[]}";
        db.update("INSERT INTO resume_versions(account_id,version,draft_revision,content) VALUES (?,1,1,?::jsonb)", owner, content);
        db.update("INSERT INTO resume_versions(account_id,version,draft_revision,content) VALUES (?,2,2,jsonb_set(?::jsonb,'{profile,name}',to_jsonb('User edit'::text)))", owner, content);
        db.update("INSERT INTO resume_drafts(account_id,revision,content) VALUES (?,3,jsonb_set(?::jsonb,'{profile,name}',to_jsonb('Unsaved submission'::text)))", owner, content);
        db.update("INSERT INTO mentor_main_conversations(account_id,first_resume_version) VALUES (?,1)", owner);
        long evidence = db.queryForObject("INSERT INTO capability_evidence(account_id,capability_id,source_type,source_id,outcome) VALUES (?,'sql','LEARNING','verified','PASSED') RETURNING id", Long.class, owner);
        assertThatThrownBy(() -> service.applyEvidence(other, 1, evidence, "10000000-0000-0000-0000-000000000001")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        var result = service.applyEvidence(owner, 1, evidence, "10000000-0000-0000-0000-000000000001");
        assertThat(result.version()).isEqualTo(3);
        assertThat(service.applyEvidence(owner, 1, evidence, "10000000-0000-0000-0000-000000000001").version()).isEqualTo(3);
        String latest = db.queryForObject("SELECT content::text FROM resume_versions WHERE account_id=? AND version=3", String.class, owner);
        assertThat(latest).contains("Unsaved submission", "SQL").contains("\"experiences\": []");
        assertThat(db.queryForObject("SELECT content::text FROM resume_drafts WHERE account_id=?", String.class, owner)).contains("Unsaved submission", "SQL");
        assertThat(db.queryForObject("SELECT count(*) FROM mentor_turns WHERE student_message IS NOT NULL AND conversation_id=(SELECT id FROM mentor_main_conversations WHERE account_id=?)", Integer.class, owner)).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM mentor_turns WHERE conversation_id=(SELECT id FROM mentor_main_conversations WHERE account_id=?)", Integer.class, owner)).isEqualTo(1);
    }
    @Test @Transactional
    void uploadedLatestVersionCannotBeReplacedByAnOlderDraft() {
        long owner = account("resume-upload-stale");
        String request = "20000000-0000-0000-0000-000000000001";
        db.update("INSERT INTO resume_uploads(account_id,request_id,file_name,content_type,sha256,extracted_text,file_bytes) VALUES (?,?::uuid,'original.pdf','application/pdf',?,'',?)", owner, request, "a".repeat(64), new byte[]{1});
        db.update("INSERT INTO resume_versions(account_id,version,upload_request_id) VALUES (?,1,?::uuid)", owner, request);
        db.update("INSERT INTO resume_drafts(account_id,revision,content) VALUES (?,1,'{}'::jsonb)", owner);
        long evidence = db.queryForObject("INSERT INTO capability_evidence(account_id,capability_id,source_type,source_id,outcome) VALUES (?,'sql','LEARNING','verified','PASSED') RETURNING id", Long.class, owner);
        assertThatThrownBy(() -> service.applyEvidence(owner,1,evidence,"20000000-0000-0000-0000-000000000002")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(db.queryForObject("SELECT count(*) FROM resume_versions WHERE account_id=?", Integer.class, owner)).isEqualTo(1);
    }
    @Test @Transactional
    void conversionPreservesOriginalAndChecksDraftAndLatestVersion() {
        long owner = account("resume-conversion"), other = account("resume-conversion-other");
        String request = "30000000-0000-0000-0000-000000000001";
        byte[] bytes = {1, 2, 3};
        db.update("INSERT INTO resume_uploads(account_id,request_id,file_name,content_type,sha256,extracted_text,file_bytes) VALUES (?,?::uuid,'original.docx','application/vnd.openxmlformats-officedocument.wordprocessingml.document',?,'原始经历与技能',?)", owner, request, "b".repeat(64), bytes);
        db.update("INSERT INTO resume_versions(account_id,version,upload_request_id) VALUES (?,1,?::uuid)", owner, request);
        db.update("INSERT INTO resume_drafts(account_id,revision,content) VALUES (?,2,'{}'::jsonb)", owner);
        assertThatThrownBy(() -> service.convertUpload(other,1,1,0)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> service.convertUpload(owner,1,1,1)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        var converted = service.convertUpload(owner,1,1,2);
        assertThat(converted.needsSupplement()).isFalse();
        assertThat(converted.version()).isEqualTo(2);
        assertThat(db.queryForObject("SELECT content::text FROM resume_drafts WHERE account_id=?", String.class, owner)).contains("原始经历与技能");
        assertThat(db.queryForObject("SELECT file_bytes FROM resume_uploads WHERE account_id=?", byte[].class, owner)).containsExactly(bytes);
        assertThatThrownBy(() -> service.convertUpload(owner,1,1,3)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test @Transactional
    void conversionOfUnparseableUploadOffersEmptyEditableDraft() {
        long owner = account("resume-conversion-empty");
        String request = "30000000-0000-0000-0000-000000000002";
        db.update("INSERT INTO resume_uploads(account_id,request_id,file_name,content_type,sha256,extracted_text,file_bytes) VALUES (?,?::uuid,'scan.pdf','application/pdf',?,'',?)", owner, request, "c".repeat(64), new byte[]{1});
        db.update("INSERT INTO resume_versions(account_id,version,upload_request_id) VALUES (?,1,?::uuid)", owner, request);
        var converted = service.convertUpload(owner,1,1,0);
        assertThat(converted.needsSupplement()).isTrue();
        assertThat(db.queryForObject("SELECT content::text FROM resume_drafts WHERE account_id=?", String.class, owner)).contains("education");
    }
    @Test @Transactional
    void staleDraftDoesNotReplaceNewerSubmittedVersion() {
        long owner = account("resume-stale-draft");
        String content = "{\"profile\":{\"name\":\"Old\",\"email\":\"\",\"phone\":\"\"},\"education\":{\"school\":\"School\",\"major\":\"\",\"degree\":\"\",\"graduation\":\"\"},\"experiences\":[],\"skills\":[]}";
        db.update("INSERT INTO resume_versions(account_id,version,draft_revision,content) VALUES (?,1,1,?::jsonb)", owner, content);
        db.update("INSERT INTO resume_versions(account_id,version,draft_revision,content) VALUES (?,2,2,jsonb_set(?::jsonb,'{profile,name}',to_jsonb('Latest'::text)))", owner, content);
        db.update("INSERT INTO resume_drafts(account_id,revision,content) VALUES (?,1,?::jsonb)", owner, content);
        long evidence = db.queryForObject("INSERT INTO capability_evidence(account_id,capability_id,source_type,source_id,outcome) VALUES (?,'sql','LEARNING','verified','PASSED') RETURNING id", Long.class, owner);
        service.applyEvidence(owner,1,evidence,"40000000-0000-0000-0000-000000000001");
        assertThat(db.queryForObject("SELECT content::text FROM resume_versions WHERE account_id=? ORDER BY version DESC LIMIT 1", String.class, owner)).contains("Latest", "SQL");
        assertThat(db.queryForObject("SELECT revision FROM resume_drafts WHERE account_id=?", Long.class, owner)).isGreaterThan(2L);
    }

    @Test @Transactional
    void existingSkillDoesNotCreateAFalseUpdate() {
        long owner = account("resume-existing-skill");
        String content = "{\"profile\":{\"name\":\"A\",\"email\":\"\",\"phone\":\"\"},\"education\":{\"school\":\"School\",\"major\":\"\",\"degree\":\"\",\"graduation\":\"\"},\"experiences\":[],\"skills\":[{\"name\":\"SQL\",\"proficiency\":\"GUIDED\",\"description\":\"\"}]}";
        db.update("INSERT INTO resume_versions(account_id,version,draft_revision,content) VALUES (?,1,1,?::jsonb)", owner, content);
        db.update("INSERT INTO mentor_main_conversations(account_id,first_resume_version) VALUES (?,1)", owner);
        long evidence = db.queryForObject("INSERT INTO capability_evidence(account_id,capability_id,source_type,source_id,outcome) VALUES (?,'sql','LEARNING','verified','PASSED') RETURNING id", Long.class, owner);
        assertThat(service.applyEvidence(owner,1,evidence,"40000000-0000-0000-0000-000000000002").version()).isEqualTo(1);
        assertThat(db.queryForObject("SELECT count(*) FROM mentor_turns WHERE conversation_id=(SELECT id FROM mentor_main_conversations WHERE account_id=?)", Integer.class, owner)).isZero();
    }
    @Test
    void concurrentConversionsCannotBothReplaceDraft() throws Exception {
        long owner = account("resume-concurrent-conversion");
        try {
            String request = "50000000-0000-0000-0000-000000000001";
            db.update("INSERT INTO resume_uploads(account_id,request_id,file_name,content_type,sha256,extracted_text,file_bytes) VALUES (?,?::uuid,'original.pdf','application/pdf',?,'content',?)", owner, request, "d".repeat(64), new byte[]{1});
            db.update("INSERT INTO resume_versions(account_id,version,upload_request_id) VALUES (?,1,?::uuid)", owner, request);
            var start = new java.util.concurrent.CountDownLatch(1);
            var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
            try {
                java.util.concurrent.Callable<Boolean> task = () -> { start.await(); try { service.convertUpload(owner,1,1,0); return true; } catch (org.springframework.web.server.ResponseStatusException conflict) { return false; } };
                var first = pool.submit(task); var second = pool.submit(task); start.countDown();
                assertThat(java.util.List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
                assertThat(db.queryForObject("SELECT count(*) FROM resume_versions WHERE account_id=?", Integer.class, owner)).isEqualTo(2);
            } finally { pool.shutdownNow(); }
        } finally { db.update("DELETE FROM accounts WHERE id=?", owner); }
    }
    @Test @Transactional
    void restoreCreatesNewVersionAndRejectsStaleEdits() {
        long owner = account("resume-restore"), other = account("resume-restore-other");
        String old = "{\"profile\":{\"name\":\"Old\",\"email\":\"\",\"phone\":\"\"},\"education\":{\"school\":\"School\",\"major\":\"\",\"degree\":\"\",\"graduation\":\"\"},\"experiences\":[],\"skills\":[]}";
        db.update("INSERT INTO resume_versions(account_id,version,draft_revision,content) VALUES (?,1,1,?::jsonb)", owner, old);
        db.update("INSERT INTO resume_versions(account_id,version,draft_revision,content) VALUES (?,2,2,jsonb_set(?::jsonb,'{profile,name}',to_jsonb('New'::text)))", owner, old);
        db.update("INSERT INTO resume_drafts(account_id,revision,content) SELECT ?,3,content FROM resume_versions WHERE account_id=? AND version=2", owner, owner);
        assertThatThrownBy(() -> service.restore(owner,1,1,3)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> service.restore(owner,1,2,2)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> service.restore(other,1,2,3)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(service.restore(owner,1,2,3).version()).isEqualTo(3);
        assertThat(db.queryForObject("SELECT content::text FROM resume_drafts WHERE account_id=?", String.class, owner)).contains("Old");
        assertThat(db.queryForObject("SELECT content::text FROM resume_versions WHERE account_id=? AND version=2", String.class, owner)).contains("New");
    }

    @Test @Transactional
    void restoringUploadedOriginalKeepsOriginalBytes() {
        long owner = account("resume-restore-upload");
        String request = "60000000-0000-0000-0000-000000000001";
        db.update("INSERT INTO resume_uploads(account_id,request_id,file_name,content_type,sha256,extracted_text,file_bytes) VALUES (?,?::uuid,'original.pdf','application/pdf',?,'text',?)", owner, request, "e".repeat(64), new byte[]{1,2,3});
        db.update("INSERT INTO resume_versions(account_id,version,upload_request_id) VALUES (?,1,?::uuid)", owner, request);
        db.update("INSERT INTO resume_versions(account_id,version,draft_revision,content) VALUES (?,2,1,'{}'::jsonb)", owner);
        assertThat(service.restore(owner,1,2,0).version()).isEqualTo(3);
        assertThat(db.queryForObject("SELECT u.file_bytes FROM resume_versions v JOIN resume_uploads u ON u.account_id=v.account_id AND u.request_id=v.upload_request_id WHERE v.account_id=? AND v.version=3", byte[].class, owner)).containsExactly(1,2,3);
        assertThat(db.queryForObject("SELECT file_bytes FROM resume_uploads WHERE account_id=? AND request_id=?::uuid", byte[].class, owner, request)).containsExactly(1,2,3);
    }
    @Test @Transactional
    void evidenceUpdateCreatesEditableDraftWhenNoneExists() {
        long owner = account("resume-no-draft");
        String content = "{\"profile\":{\"name\":\"A\",\"email\":\"\",\"phone\":\"\"},\"education\":{\"school\":\"School\",\"major\":\"\",\"degree\":\"\",\"graduation\":\"\"},\"experiences\":[],\"skills\":[]}";
        db.update("INSERT INTO resume_versions(account_id,version,draft_revision,content) VALUES (?,1,1,?::jsonb)", owner, content);
        long evidence = db.queryForObject("INSERT INTO capability_evidence(account_id,capability_id,source_type,source_id,outcome) VALUES (?,'sql','LEARNING','verified','PASSED') RETURNING id", Long.class, owner);
        service.applyEvidence(owner,1,evidence,"70000000-0000-0000-0000-000000000001");
        assertThat(db.queryForObject("SELECT content::text FROM resume_drafts WHERE account_id=?", String.class, owner)).contains("SQL");
        assertThat(db.queryForObject("SELECT revision FROM resume_drafts WHERE account_id=?", Long.class, owner)).isGreaterThan(1L);
    }
    @Test @Transactional
    void unsubmittedSkillIsNotBoundToVersionWithoutThatSkill() {
        long owner = account("resume-skill-only-in-draft");
        String content = "{\"profile\":{\"name\":\"A\",\"email\":\"\",\"phone\":\"\"},\"education\":{\"school\":\"School\",\"major\":\"\",\"degree\":\"\",\"graduation\":\"\"},\"experiences\":[],\"skills\":[]}";
        db.update("INSERT INTO resume_versions(account_id,version,draft_revision,content) VALUES (?,1,1,?::jsonb)", owner, content);
        db.update("INSERT INTO resume_drafts(account_id,revision,content) VALUES (?,2,jsonb_set(?::jsonb,'{skills}','[{\"name\":\"SQL\",\"proficiency\":\"GUIDED\",\"description\":\"\"}]'::jsonb))", owner, content);
        long evidence = db.queryForObject("INSERT INTO capability_evidence(account_id,capability_id,source_type,source_id,outcome) VALUES (?,'sql','LEARNING','verified','PASSED') RETURNING id", Long.class, owner);
        assertThatThrownBy(() -> service.applyEvidence(owner,1,evidence,"80000000-0000-0000-0000-000000000001")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(db.queryForObject("SELECT count(*) FROM resume_evidence_updates WHERE account_id=?", Integer.class, owner)).isZero();
    }
    private long account(String name) { return db.queryForObject("INSERT INTO accounts(email,display_name,password_hash,role) VALUES (?,?,'fixture','STUDENT') RETURNING id", Long.class, name+"@test.invalid", name); }
}
