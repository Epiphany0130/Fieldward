package com.fieldward.jobs;

import com.fieldward.PostgresIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;

class JobMatchStoreTest extends PostgresIntegrationTest {
    @Autowired JdbcTemplate db;
    @Autowired JobMatchStore store;
    @Test @Transactional void accountEvidenceAndDiagnosisAreIsolatedAndJobVersionsAreDistinct() {
        long a = db.queryForObject("INSERT INTO accounts(email,display_name,password_hash,role) VALUES ('match-a@test','a','x','STUDENT') RETURNING id", Long.class);
        long b = db.queryForObject("INSERT INTO accounts(email,display_name,password_hash,role) VALUES ('match-b@test','b','x','STUDENT') RETURNING id", Long.class);
        db.update("INSERT INTO job_sources(id,name,authorized) VALUES ('match-test','test',true)");
        long job = db.queryForObject("INSERT INTO jobs(source_id,source_job_id) VALUES ('match-test','x') RETURNING id", Long.class);
        long version = db.queryForObject("INSERT INTO job_versions(job_id,title,requirements,source_url) VALUES (?,'title','熟悉 SQL','https://example.com') RETURNING id", Long.class, job);
        var session = UUID.randomUUID();
        db.update("INSERT INTO realtime_sessions(id,account_id,start_request,purpose,mode,status) VALUES (?,?,'match-test','DIAGNOSIS','PHONE','COMPLETED')", session,a);
        db.update("INSERT INTO diagnosis_reports(session_id,account_id,request_id,input,report) VALUES (?,?,'x','{}','{}')", session,a);
        db.update("INSERT INTO capability_evidence(account_id,capability_id,source_type,source_id,outcome) VALUES (?,'sql','LEARNING','lesson','PASSED')",a);
        store.saveRequirements(version, java.util.List.of(new JobMatchService.Requirement("sql","SQL","熟悉 SQL")));
        var first = store.match(a,version);
        assertThat(first.result().percent()).isNull();
        assertThat(first.evidenceVersion()).isGreaterThan(0);
        db.update("INSERT INTO capability_evidence(account_id,capability_id,source_type,source_id,outcome) VALUES (?,'sql','INTERVIEW','later','NEEDS_PRACTICE')",a);
        var refreshed = store.match(a,version);
        assertThat(refreshed.result().percent()).isNull();
        assertThat(refreshed.evidenceVersion()).isGreaterThan(first.evidenceVersion());
        assertThat(store.match(b,version).result().percent()).isNull();
        db.update("INSERT INTO job_versions(job_id,title,requirements,source_url) VALUES (?,'title','SQL 和 Java','https://example.com')",job);
        long newer = db.queryForObject("SELECT max(id) FROM job_versions WHERE job_id=?", Long.class, job);
        assertThat(store.match(a,newer).result().percent()).isNull();
        assertThat(store.match(a,newer).status()).isEqualTo("PENDING");
    }
    @Test @Transactional void queueClaimsAreIdempotentAndFailedJobsCanRetry() {
        db.update("INSERT INTO job_sources(id,name,authorized) VALUES ('match-queue','test',true)");
        long job = db.queryForObject("INSERT INTO jobs(source_id,source_job_id) VALUES ('match-queue','x') RETURNING id", Long.class);
        long version = db.queryForObject("INSERT INTO job_versions(job_id,title,requirements,source_url) VALUES (?,'title','SQL','https://example.com') RETURNING id", Long.class, job);
        store.enqueue(version); store.enqueue(version);
        assertThat(db.queryForObject("SELECT count(*) FROM job_requirement_snapshots WHERE job_version_id=?",Integer.class,version)).isEqualTo(1);
        var claim = store.claim();
        assertThat(claim).isNotNull();
        assertThat(store.claim()).isNull();
        store.fail(claim);
        db.update("UPDATE job_requirement_snapshots SET lease_until=now()-interval '1 second' WHERE job_version_id=?",version);
        assertThat(store.claim()).isNotNull();
        db.update("UPDATE job_requirement_snapshots SET status='FAILED',attempts=3,lease_until=now()-interval '1 second' WHERE job_version_id=?",version);
        assertThat(store.claim()).isNull();
    }
    @Test @Transactional void skipsRevokedAndSupersededVersions() {
        db.update("INSERT INTO job_sources(id,name,authorized) VALUES ('match-stale','test',true)");
        long job = db.queryForObject("INSERT INTO jobs(source_id,source_job_id) VALUES ('match-stale','x') RETURNING id", Long.class);
        long version = db.queryForObject("INSERT INTO job_versions(job_id,title,requirements,source_url) VALUES (?,'title','SQL','https://example.com') RETURNING id", Long.class, job);
        store.enqueue(version);
        db.update("UPDATE job_sources SET authorized=false WHERE id='match-stale'");
        assertThat(store.claim()).isNull();
        db.update("UPDATE job_sources SET authorized=true WHERE id='match-stale'");
        db.update("INSERT INTO job_versions(job_id,title,requirements,source_url) VALUES (?,'new','Java','https://example.com')", job);
        assertThat(store.claim()).isNull();
    }
    @Test @Transactional void expiredFinalAttemptBecomesFailed() {
        db.update("INSERT INTO job_sources(id,name,authorized) VALUES ('match-exhausted','test',true)");
        long job = db.queryForObject("INSERT INTO jobs(source_id,source_job_id) VALUES ('match-exhausted','x') RETURNING id", Long.class);
        long version = db.queryForObject("INSERT INTO job_versions(job_id,title,requirements,source_url) VALUES (?,'title','SQL','https://example.com') RETURNING id", Long.class, job);
        store.enqueue(version);
        db.update("UPDATE job_requirement_snapshots SET status='PROCESSING',attempts=3,lease_until=now()-interval '1 second' WHERE job_version_id=?",version);
        assertThat(store.claim()).isNull();
        assertThat(db.queryForObject("SELECT status FROM job_requirement_snapshots WHERE job_version_id=?",String.class,version)).isEqualTo("FAILED");
    }
}
