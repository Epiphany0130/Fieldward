package com.fieldward.mentor;

import com.fieldward.PostgresIntegrationTest;
import com.fieldward.credits.CreditService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

class MentorCreditTest extends PostgresIntegrationTest {
    @Autowired JdbcTemplate db;
    @Autowired MentorTurns turns;
    @Autowired CreditService credits;
    @Autowired com.fieldward.credits.ExpiredCreditCleanup expiredCredits;
    @Autowired CareerWrites careerWrites;
    @Autowired SkillSessions skills;

    @Test void emptyAccountAndInterruptedRetryAndCompletedReplay() {
        db.update("UPDATE credit_allocation_config SET initial_points = 0 WHERE id = true");
        long id = db.queryForObject("INSERT INTO accounts(email,display_name,password_hash,role) VALUES ('mentor-credit@example.test','Test','hash','STUDENT') RETURNING id", Long.class);
        db.update("UPDATE credit_allocation_config SET initial_points = 50 WHERE id = true");
        db.update("DELETE FROM credit_stage_reserves WHERE account_id = ?", id);
        db.update("INSERT INTO resume_versions(account_id,version,draft_revision,content) VALUES (?,1,1,'{}')", id);
        db.update("INSERT INTO mentor_main_conversations(account_id,first_resume_version,career_onboarding_required) VALUES (?,1,false)", id);
        String request = UUID.randomUUID().toString();
        assertThatThrownBy(() -> turns.claim(id, null, request, "hello", true)).isInstanceOf(RuntimeException.class);
        assertThat(db.queryForObject("SELECT count(*) FROM mentor_turns", Integer.class)).isZero();
        credits.adjust(id, id, 1, "test", "mentor-test-grant");
        var first = turns.claim(id, null, request, "hello", true);
        turns.interrupt(first);
        turns.interrupt(first);
        var retry = turns.claim(id, null, request, "hello", true);
        assertThat(retry.attempt()).isEqualTo(2);
        assertThat(turns.complete(retry, "answer", List.of())).isNotNull();
        turns.interrupt(retry);
        assertThat(turns.claim(id, null, request, "hello", true).status()).isEqualTo(MentorTurnMapper.Status.COMPLETED);
        assertThat(db.queryForObject("SELECT balance FROM credit_accounts WHERE account_id = ?", Long.class, id)).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM credit_ledger WHERE account_id = ? AND kind = 'CONSUME'", Integer.class, id)).isEqualTo(1);
    }

    @Test void careerAssessmentReservesAndReleasesOnFailure() {
        db.update("UPDATE credit_allocation_config SET initial_points = 0 WHERE id = true");
        long id = db.queryForObject("INSERT INTO accounts(email,display_name,password_hash,role) VALUES ('career-credit@example.test','Test','hash','STUDENT') RETURNING id", Long.class);
        db.update("UPDATE credit_allocation_config SET initial_points = 50 WHERE id = true");
        db.update("DELETE FROM credit_stage_reserves WHERE account_id = ?", id);
        db.update("INSERT INTO resume_versions(account_id,version,draft_revision,content) VALUES (?,1,1,CAST(? AS jsonb))", id, "{\"education\":{\"school\":\"University\"}}");
        db.update("INSERT INTO mentor_main_conversations(account_id,first_resume_version) VALUES (?,1)", id);
        assertThatThrownBy(() -> careerWrites.claim(id, "java-backend", 1)).isInstanceOf(RuntimeException.class);
        credits.adjust(id, id, 1, "test", "career-test-grant");
        var claim = careerWrites.claim(id, "java-backend", 1);
        assertThat(claim.start()).isTrue();
        assertThatThrownBy(() -> credits.reserveAi(id, "competing-career", "DIAGNOSIS")).isInstanceOf(RuntimeException.class);
        careerWrites.fail(claim.assessment());
        var retry = careerWrites.claim(id, "java-backend", 1);
        assertThat(retry.assessment().attempt()).isEqualTo(2);
        careerWrites.fail(retry.assessment());
        assertThat(db.queryForObject("SELECT balance FROM credit_accounts WHERE account_id = ?", Long.class, id)).isEqualTo(1L);
    }

    @Test void skillEntryWithNoBalanceDoesNotLockProgress() {
        db.update("UPDATE credit_allocation_config SET initial_points = 0 WHERE id = true");
        long id = db.queryForObject("INSERT INTO accounts(email,display_name,password_hash,role) VALUES ('skills-credit@example.test','Test','hash','STUDENT') RETURNING id", Long.class);
        db.update("UPDATE credit_allocation_config SET initial_points = 50 WHERE id = true");
        db.update("DELETE FROM credit_stage_reserves WHERE account_id = ?", id);
        db.update("INSERT INTO resume_versions(account_id,version,draft_revision,content) VALUES (?,1,1,'{}')", id);
        long conversation = db.queryForObject("INSERT INTO mentor_main_conversations(account_id,first_resume_version) VALUES (?,1) RETURNING id", Long.class, id);
        long side = db.queryForObject("INSERT INTO mentor_side_chats(conversation_id,title) VALUES (?,'skills') RETURNING id", Long.class, conversation);
        long assessment = db.queryForObject("INSERT INTO career_assessments(account_id,resume_version,direction_id,source_digest,status,attempt,report) VALUES (?,1,'java-backend',?,'READY',1,'{}') RETURNING id", Long.class, id, "f".repeat(64));
        String state = "{\"phase\":\"PREPARING\",\"topics\":[\"SQL\"],\"selfMarks\":{\"SQL\":\"GREEN\"},\"testTopics\":[\"SQL\"],\"grades\":[],\"studyTopics\":[],\"lessonIndex\":0}";
        db.update("INSERT INTO career_skill_sessions(account_id,assessment_id,conversation_id,side_chat_id,state) VALUES (?,?,?,?,CAST(? AS jsonb))", id, assessment, conversation, side, state);
        assertThatThrownBy(() -> skills.step(id, assessment, 0, null)).isInstanceOf(RuntimeException.class);
        assertThat(db.queryForObject("SELECT claim_id IS NULL FROM career_skill_sessions WHERE account_id = ?", Boolean.class, id)).isTrue();
    }

    @Test void expiredHoldIsReleasedEvenWhenNewRequestFails() {
        db.update("UPDATE credit_allocation_config SET initial_points = 0 WHERE id = true");
        long id = db.queryForObject("INSERT INTO accounts(email,display_name,password_hash,role) VALUES ('expiry-credit@example.test','Test','hash','STUDENT') RETURNING id", Long.class);
        db.update("UPDATE credit_allocation_config SET initial_points = 50 WHERE id = true");
        db.update("DELETE FROM credit_stage_reserves WHERE account_id = ?", id);
        db.update("INSERT INTO resume_versions(account_id,version,draft_revision,content) VALUES (?,1,1,'{}')", id);
        long conversation = db.queryForObject("INSERT INTO mentor_main_conversations(account_id,first_resume_version,career_onboarding_required) VALUES (?,1,false) RETURNING id", Long.class, id);
        credits.adjust(id, id, 1, "test", "expiry-grant");
        var first = turns.claim(id, null, UUID.randomUUID().toString(), "hello", true);
        long side = db.queryForObject("INSERT INTO mentor_side_chats(conversation_id,title) VALUES (?,'other') RETURNING id", Long.class, conversation);
        assertThatThrownBy(() -> turns.claim(id, side, UUID.randomUUID().toString(), "hello", false)).isInstanceOf(RuntimeException.class);
        assertThat(db.queryForObject("SELECT status FROM credit_reservations WHERE request_id = ?", String.class, "mentor:" + first.id() + ":" + first.attempt())).isEqualTo("ACTIVE");
        db.update("UPDATE mentor_turns SET lease_until = now() - interval '1 second' WHERE id = ?", first.id());
        assertThatThrownBy(() -> turns.claim(id, side, UUID.randomUUID().toString(), "hello", false)).isInstanceOf(RuntimeException.class);
        assertThat(db.queryForObject("SELECT status FROM credit_reservations WHERE request_id = ?", String.class, "mentor:" + first.id() + ":" + first.attempt())).isEqualTo("RELEASED");
        assertThat(db.queryForObject("SELECT status FROM mentor_turns WHERE id = ?", String.class, first.id())).isEqualTo("INTERRUPTED");
    }

    @Test void completedDiagnosisAndSkillsReleaseUnusedProtection() throws Exception {
        long id = db.queryForObject("INSERT INTO accounts(email,display_name,password_hash,role) VALUES ('stage-complete@example.test','Test','hash','STUDENT') RETURNING id", Long.class);
        db.update("INSERT INTO resume_versions(account_id,version,draft_revision,content) VALUES (?,1,1,'{}')", id);
        long conversation = db.queryForObject("INSERT INTO mentor_main_conversations(account_id,first_resume_version) VALUES (?,1) RETURNING id", Long.class, id);
        var claim = careerWrites.claim(id, "java-backend", 1);
        var report = new CareerFit(null, new tools.jackson.databind.json.JsonMapper()).analyze(CareerCatalog.require("java-backend"), new CareerFit.Input("", null, false));
        careerWrites.fail(claim.assessment());
        db.update("UPDATE resume_versions SET content = '{}' WHERE account_id = ?", id);
        db.update("UPDATE career_assessments SET status='ANALYZING', lease_until=now()+interval '2 minutes' WHERE id=?", claim.assessment().id());
        careerWrites.finish(new CareerWrites.Claim(claim.assessment(), new CareerFit.Input("", null, false), true, null), report);
        assertThat(db.queryForObject("SELECT points FROM credit_stage_reserves WHERE account_id=? AND stage='DIAGNOSIS'", Long.class, id)).isZero();
        long side = db.queryForObject("INSERT INTO mentor_side_chats(conversation_id,title) VALUES (?,'skills') RETURNING id", Long.class, conversation);
        String state = "{\"phase\":\"REPORT\",\"topics\":[],\"selfMarks\":{},\"testTopics\":[],\"grades\":[],\"studyTopics\":[],\"lessonIndex\":0}";
        db.update("INSERT INTO career_skill_sessions(account_id,assessment_id,conversation_id,side_chat_id,state) VALUES (?,?,?,?,CAST(? AS jsonb))", id, claim.assessment().id(), conversation, side, state);
        skills.step(id, claim.assessment().id(), 0, null);
        skills.step(id, claim.assessment().id(), 0, null);
        assertThat(db.queryForObject("SELECT points FROM credit_stage_reserves WHERE account_id=? AND stage='SKILLS'", Long.class, id)).isZero();
    }

    @Test void expiredCareerAndSkillLeasesAreRecoveredAcrossEntries() {
        long id = db.queryForObject("INSERT INTO accounts(email,display_name,password_hash,role) VALUES ('cleanup-kinds@example.test','Test','hash','STUDENT') RETURNING id", Long.class);
        db.update("INSERT INTO resume_versions(account_id,version,draft_revision,content) VALUES (?,1,1,'{}')", id);
        long conversation = db.queryForObject("INSERT INTO mentor_main_conversations(account_id,first_resume_version) VALUES (?,1) RETURNING id", Long.class, id);
        long side = db.queryForObject("INSERT INTO mentor_side_chats(conversation_id,title) VALUES (?,'skills') RETURNING id", Long.class, conversation);
        long assessment = db.queryForObject("INSERT INTO career_assessments(account_id,resume_version,direction_id,source_digest,status,attempt,lease_until) VALUES (?,1,'java-backend',?,'ANALYZING',1,now()-interval '1 second') RETURNING id", Long.class, id, "a".repeat(64));
        String claim = UUID.randomUUID().toString();
        db.update("INSERT INTO career_skill_sessions(account_id,assessment_id,conversation_id,side_chat_id,state,claim_id,lease_until) VALUES (?,?,?,?,'{}',CAST(? AS uuid),now()-interval '1 second')", id, assessment, conversation, side, claim);
        credits.reserveAi(id, "career:" + assessment + ":1", "DIAGNOSIS");
        credits.reserveAi(id, "skill:" + claim, "SKILLS");
        expiredCredits.recover(id);
        expiredCredits.recover(id);
        assertThat(db.queryForObject("SELECT status FROM career_assessments WHERE id=?", String.class, assessment)).isEqualTo("FAILED");
        assertThat(db.queryForObject("SELECT claim_id IS NULL FROM career_skill_sessions WHERE account_id=?", Boolean.class, id)).isTrue();
        assertThat(db.queryForObject("SELECT count(*) FROM credit_reservations WHERE account_id=? AND status='RELEASED'", Integer.class, id)).isEqualTo(2);
    }

    @Test void viewingAndAdministrativeAdjustmentRecoverExpiredHolds() {
        db.update("UPDATE credit_allocation_config SET initial_points=0 WHERE id=true");
        long id = db.queryForObject("INSERT INTO accounts(email,display_name,password_hash,role) VALUES ('view-cleanup@example.test','Test','hash','STUDENT') RETURNING id", Long.class);
        db.update("UPDATE credit_allocation_config SET initial_points=50 WHERE id=true");
        db.update("DELETE FROM credit_stage_reserves WHERE account_id=?", id);
        db.update("INSERT INTO resume_versions(account_id,version,draft_revision,content) VALUES (?,1,1,'{}')", id);
        db.update("INSERT INTO mentor_main_conversations(account_id,first_resume_version,career_onboarding_required) VALUES (?,1,false)", id);
        credits.adjust(id,id,1,"grant","view-cleanup-grant");
        var turn = turns.claim(id,null,UUID.randomUUID().toString(),"hello",true);
        db.update("UPDATE mentor_turns SET lease_until=now()-interval '1 second' WHERE id=?", turn.id());
        credits.view(id);
        assertThat(db.queryForObject("SELECT status FROM credit_reservations WHERE request_id=?", String.class, "mentor:"+turn.id()+":"+turn.attempt())).isEqualTo("RELEASED");
        var second = turns.claim(id,null,UUID.randomUUID().toString(),"hello",true);
        db.update("UPDATE mentor_turns SET lease_until=now()-interval '1 second' WHERE id=?", second.id());
        credits.adjust(id,id,-1,"remove","view-cleanup-remove");
        assertThat(db.queryForObject("SELECT status FROM credit_reservations WHERE request_id=?", String.class, "mentor:"+second.id()+":"+second.attempt())).isEqualTo("RELEASED");
    }

    @Test void concurrentCompletionAndRecoveryKeepTurnAndLedgerConsistent() throws Exception {
        long id = db.queryForObject("INSERT INTO accounts(email,display_name,password_hash,role) VALUES ('race-cleanup@example.test','Test','hash','STUDENT') RETURNING id", Long.class);
        db.update("INSERT INTO resume_versions(account_id,version,draft_revision,content) VALUES (?,1,1,'{}')", id);
        db.update("INSERT INTO mentor_main_conversations(account_id,first_resume_version,career_onboarding_required) VALUES (?,1,false)", id);
        var turn = turns.claim(id,null,UUID.randomUUID().toString(),"hello",true);
        var gate = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var completion = executor.submit(() -> { gate.await(); return turns.complete(turn, "answer", List.of()); });
            var recovery = executor.submit(() -> { gate.await(); expiredCredits.recover(id); return null; });
            gate.countDown();
            assertThat(completion.get()).isNotNull();
            recovery.get();
        } finally { executor.shutdownNow(); }
        assertThat(db.queryForObject("SELECT status FROM mentor_turns WHERE id=?", String.class, turn.id())).isEqualTo("COMPLETED");
        assertThat(db.queryForObject("SELECT status FROM credit_reservations WHERE request_id=?", String.class, "mentor:"+turn.id()+":"+turn.attempt())).isEqualTo("SETTLED");
        assertThat(db.queryForObject("SELECT count(*) FROM credit_ledger WHERE request_id=?", Integer.class, "mentor:"+turn.id()+":"+turn.attempt())).isEqualTo(1);
    }

    @Test void diagnosisCompletionDoesNotHoldCreditsWhileWaitingForConversation() throws Exception {
        long id = db.queryForObject("INSERT INTO accounts(email,display_name,password_hash,role) VALUES ('lock-order@example.test','Test','hash','STUDENT') RETURNING id", Long.class);
        db.update("INSERT INTO resume_versions(account_id,version,draft_revision,content) VALUES (?,1,1,'{}')", id);
        long conversation = db.queryForObject("INSERT INTO mentor_main_conversations(account_id,first_resume_version) VALUES (?,1) RETURNING id", Long.class, id);
        var claim = careerWrites.claim(id, "java-backend", 1);
        var report = new CareerFit(null, new tools.jackson.databind.json.JsonMapper()).analyze(CareerCatalog.require("java-backend"), new CareerFit.Input("", null, false));
        var completing = new CareerWrites.Claim(claim.assessment(), claim.input(), true, claim.assessment().id());
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try (var connection = db.getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            int blocker;
            try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT pg_backend_pid()")) { result.next(); blocker = result.getInt(1); }
            try (var statement = connection.prepareStatement("SELECT id FROM mentor_main_conversations WHERE id=? FOR UPDATE")) { statement.setLong(1, conversation); statement.executeQuery().close(); }
            var task = executor.submit(() -> { careerWrites.finish(completing, report); return null; });
            try {
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
                while (db.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid))", Integer.class, blocker) == 0 && System.nanoTime() < deadline) Thread.sleep(20);
                assertThat(db.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid))", Integer.class, blocker)).isPositive();
                try (var probe = db.getDataSource().getConnection(); var statement = probe.prepareStatement("SELECT account_id FROM credit_accounts WHERE account_id=? FOR UPDATE NOWAIT")) {
                    statement.setLong(1, id);
                    statement.executeQuery().close();
                }
            } finally {
                connection.rollback();
                task.get(5, java.util.concurrent.TimeUnit.SECONDS);
            }
        } finally { executor.shutdownNow(); }
    }
}
