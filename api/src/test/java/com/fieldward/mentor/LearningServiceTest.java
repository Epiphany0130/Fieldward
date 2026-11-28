package com.fieldward.mentor;

import com.fieldward.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

class LearningServiceTest extends PostgresIntegrationTest {
    @Autowired JdbcTemplate db;
    @Autowired LearningService service;
    @Autowired MentorTurns turns;
    @Autowired com.fieldward.credits.CreditService credits;
    @Autowired com.fieldward.credits.ExpiredCreditCleanup cleanup;
    @org.springframework.test.context.bean.override.mockito.MockitoBean MentorModel model;
    @Autowired tools.jackson.databind.json.JsonMapper json;

    @Test @Transactional
    void assessesFreshAnswerOnceAndPreservesExistingEvidence() throws Exception {
        long owner = account("verify-owner"), other = account("verify-other");
        conversation(owner); conversation(other);
        service.openTopic(owner, "sql");
        db.update("UPDATE credit_accounts SET balance=1000 WHERE account_id=?", owner);
        db.update("INSERT INTO capability_evidence(account_id,capability_id,source_type,source_id,outcome) VALUES (?,'sql','DIAGNOSIS','old','PASSED')", owner);
        org.mockito.Mockito.when(model.available()).thenReturn(true);
        org.mockito.Mockito.when(model.completeJson(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(json.readTree("{\"question\":\"如何设计索引？\"}"), json.readTree("{\"outcome\":\"NEEDS_PRACTICE\",\"feedback\":\"解释索引选择依据\"}"));
        var question = service.startVerification(owner, "sql", "request-1");
        assertThat(question.question()).contains("索引");
        assertThat(service.startVerification(owner, "sql", "request-1").id()).isEqualTo(question.id());
        assertThatThrownBy(() -> service.submitVerification(other, question.id(), "回答"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(db.queryForObject("SELECT count(*) FROM capability_evidence WHERE account_id=?", Integer.class, owner)).isEqualTo(1);
        var result = service.submitVerification(owner, question.id(), "先分析查询条件");
        assertThat(result.outcome()).isEqualTo("NEEDS_PRACTICE");
        assertThat(result.feedback()).contains("索引");
        assertThat(service.submitVerification(owner, question.id(), "先分析查询条件").evidenceId()).isEqualTo(result.evidenceId());
        assertThatThrownBy(() -> service.submitVerification(owner, question.id(), "不同答案"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(db.queryForObject("SELECT count(*) FROM capability_evidence WHERE account_id=?", Integer.class, owner)).isEqualTo(2);
        assertThat(db.queryForObject("SELECT count(*) FROM credit_ledger WHERE account_id=? AND kind='CONSUME'", Integer.class, owner)).isEqualTo(2);
        assertThat(db.queryForObject("SELECT count(*) FROM credit_reservations WHERE account_id=? AND stage='FREE'", Integer.class, owner)).isEqualTo(2);
    }


    @Test @Transactional
    void freelyOpensAndReusesAccountScopedTopicWithoutChangingEvidence() {
        long owner = account("learning-owner"), other = account("learning-other");
        conversation(owner); conversation(other);
        db.update("INSERT INTO capability_evidence(account_id,capability_id,source_type,source_id,outcome) VALUES (?,'sql','DIAGNOSIS','old','PASSED')", owner);
        db.update("INSERT INTO mentor_side_chats(conversation_id,title) SELECT id,'学习：SQL' FROM mentor_main_conversations WHERE account_id=?", owner);
        var first = service.openTopic(owner, "sql");
        assertThat(service.openTopic(owner, "sql").id()).isEqualTo(first.id());
        var initial = turns.history(owner, Long.valueOf(first.id()), null, null, true).turns();
        assertThat(initial).hasSize(1);
        assertThat(initial.getFirst().mentorReply()).contains("SQL", "？");
        db.update("UPDATE mentor_main_conversations SET career_onboarding_required=false WHERE account_id=?", owner);
        var turn = turns.claim(owner, Long.valueOf(first.id()), "10000000-0000-0000-0000-000000000011", "请教我", true);
        assertThat(turns.context(turn).messages().toString()).contains("SQL", "表结构", "练习");
        assertThat(service.openTopic(other, "sql").id()).isNotEqualTo(first.id());
        assertThat(service.openTopic(owner, "java-backend:api").id()).isNotEqualTo(first.id());
        assertThat(db.queryForObject("SELECT count(*) FROM capability_evidence WHERE account_id=?", Integer.class, owner)).isEqualTo(1);
        assertThatThrownBy(() -> service.openTopic(owner, "does-not-exist")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
    @Test @Transactional
    void duplicateAndFailedGenerationDoNotChargeOrCreateEvidence() throws Exception {
        long owner = account("verify-failure"); conversation(owner);
        db.update("UPDATE credit_accounts SET balance=1000 WHERE account_id=?", owner);
        service.openTopic(owner, "sql");
        db.update("INSERT INTO learning_verifications(account_id,capability_id,request_id,question) VALUES (?,'sql','earlier','旧题')", owner);
        org.mockito.Mockito.when(model.available()).thenReturn(true);
        org.mockito.Mockito.when(model.completeJson(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(json.readTree("{\"question\":\"旧题\"}"))
            .thenThrow(new java.io.IOException("provider unavailable"))
            .thenReturn(json.readTree("{\"question\":\"新题\"}"));
        assertThatThrownBy(() -> service.startVerification(owner, "sql", "retry")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> service.startVerification(owner, "sql", "retry")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(db.queryForObject("SELECT count(*) FROM credit_ledger WHERE account_id=? AND kind='CONSUME'", Integer.class, owner)).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM capability_evidence WHERE account_id=?", Integer.class, owner)).isZero();
        assertThat(service.startVerification(owner, "sql", "retry").question()).isEqualTo("新题");
        assertThat(db.queryForObject("SELECT count(*) FROM credit_ledger WHERE account_id=? AND kind='CONSUME'", Integer.class, owner)).isEqualTo(1);
    }

    @Test
    void concurrentRequestDoesNotCallModelTwice() throws Exception {
        long owner = account("verify-concurrent-" + java.util.UUID.randomUUID()); conversation(owner);
        db.update("UPDATE credit_accounts SET balance=1000 WHERE account_id=?", owner);
        service.openTopic(owner, "sql");
        var entered = new java.util.concurrent.CountDownLatch(1);
        var proceed = new java.util.concurrent.CountDownLatch(1);
        org.mockito.Mockito.when(model.available()).thenReturn(true);
        org.mockito.Mockito.when(model.completeJson(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any()))
            .thenAnswer(invocation -> { entered.countDown(); if (!proceed.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new java.io.IOException("timeout"); return json.readTree("{\"question\":\"新题\"}"); });
        var pool = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var first = pool.submit(() -> service.startVerification(owner, "sql", "concurrent"));
            assertThat(entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> service.startVerification(owner, "sql", "concurrent"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
            proceed.countDown();
            assertThat(first.get(5, java.util.concurrent.TimeUnit.SECONDS).question()).isEqualTo("新题");
            assertThat(service.startVerification(owner, "sql", "concurrent").question()).isEqualTo("新题");
            org.mockito.Mockito.verify(model, org.mockito.Mockito.times(1)).completeJson(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any());
        } finally { proceed.countDown(); pool.shutdownNow(); }
    }

    @Test
    void expiredClaimReleasesReservedCredit() {
        long owner = account("verify-expired-" + java.util.UUID.randomUUID()); conversation(owner);
        db.update("UPDATE credit_accounts SET balance=1000 WHERE account_id=?", owner);
        db.update("INSERT INTO learning_verifications(account_id,capability_id,request_id,claim_id,lease_until) VALUES (?,'sql','expired','test-expired',now()-interval '1 second')", owner);
        credits.reserveAi(owner, "learning:test-expired", "SKILLS");
        cleanup.recover(owner);
        assertThat(db.queryForObject("SELECT status FROM credit_reservations WHERE request_id='learning:test-expired'", String.class)).isEqualTo("RELEASED");
        assertThat(db.queryForObject("SELECT claim_id FROM learning_verifications WHERE account_id=?", String.class, owner)).isNull();
    }

    @Test
    void simultaneousRequestsCannotPublishTheSameQuestion() throws Exception {
        long owner = account("verify-duplicate-" + java.util.UUID.randomUUID()); conversation(owner);
        db.update("UPDATE credit_accounts SET balance=1000 WHERE account_id=?", owner);
        service.openTopic(owner, "sql");
        var entered = new java.util.concurrent.CountDownLatch(2);
        var proceed = new java.util.concurrent.CountDownLatch(1);
        org.mockito.Mockito.when(model.available()).thenReturn(true);
        org.mockito.Mockito.when(model.completeJson(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any()))
            .thenAnswer(invocation -> { entered.countDown(); if (!proceed.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new java.io.IOException("timeout"); return json.readTree("{\"question\":\"相同的题\"}"); });
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> service.startVerification(owner, "sql", "one"));
            var second = pool.submit(() -> service.startVerification(owner, "sql", "two"));
            assertThat(entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            proceed.countDown();
            int successes = 0;
            for (var future : java.util.List.of(first, second)) {
                try { future.get(5, java.util.concurrent.TimeUnit.SECONDS); successes++; }
                catch (java.util.concurrent.ExecutionException expected) { assertThat(expected.getCause()).isInstanceOf(org.springframework.web.server.ResponseStatusException.class); }
            }
            assertThat(successes).isEqualTo(1);
            assertThat(db.queryForObject("SELECT count(*) FROM credit_ledger WHERE account_id=? AND kind='CONSUME'", Integer.class, owner)).isEqualTo(1);
        } finally { proceed.countDown(); pool.shutdownNow(); }
    }

    private long account(String name) { return db.queryForObject("INSERT INTO accounts(email,display_name,password_hash,role) VALUES (?,?, 'fixture','STUDENT') RETURNING id", Long.class, name+"@test.invalid", name); }
    private void conversation(long account) {
        db.update("INSERT INTO resume_versions(account_id,version,draft_revision,content) VALUES (?,1,1,'{}')", account);
        db.update("INSERT INTO mentor_main_conversations(account_id,first_resume_version) VALUES (?,1)", account);
    }
}
