package com.fieldward.credits;

import com.fieldward.PostgresIntegrationTest;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

class CreditServiceTest extends PostgresIntegrationTest {
    @Autowired CreditService credits;
    @Autowired JdbcTemplate db;

    long account(String email) {
        db.update("UPDATE credit_allocation_config SET initial_points = 0 WHERE id = true");
        long id = db.queryForObject("INSERT INTO accounts(email, display_name, password_hash, role) VALUES (?, 'Test', 'hash', 'STUDENT') RETURNING id", Long.class, email);
        db.update("UPDATE credit_allocation_config SET initial_points = 50 WHERE id = true");
        db.update("DELETE FROM credit_stage_reserves WHERE account_id = ?", id);
        return id;
    }

    @Test void concurrencyIdempotenceAndProtectedStages() throws Exception {
        long id = account("credits-test@example.test");
        credits.adjust(id, id, 100, "test allocation", "grant-1");
        credits.protect(id, "DIAGNOSIS", 60);
        assertThatThrownBy(() -> credits.reserve(id, "free-too-large", "FREE", 41)).isInstanceOf(RuntimeException.class);
        var executor = Executors.newFixedThreadPool(2);
        var gate = new CountDownLatch(1);
        try {
            Callable<Boolean> a = () -> { gate.await(); try { credits.reserve(id, "race-a", "FREE", 30); return true; } catch (RuntimeException e) { return false; } };
            Callable<Boolean> b = () -> { gate.await(); try { credits.reserve(id, "race-b", "FREE", 30); return true; } catch (RuntimeException e) { return false; } };
            var first = executor.submit(a); var second = executor.submit(b); gate.countDown();
            assertThat((first.get() ? 1 : 0) + (second.get() ? 1 : 0)).isEqualTo(1);
        } finally { executor.shutdownNow(); }
        credits.reserve(id, "diagnosis", "DIAGNOSIS", 60);
        credits.settle("diagnosis", 20);
        credits.settle("diagnosis", 20);
        credits.release("diagnosis");
        assertThat(db.queryForObject("SELECT count(*) FROM credit_ledger WHERE request_id = 'diagnosis' AND kind = 'CONSUME'", Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT balance FROM credit_accounts WHERE account_id = ?", Long.class, id)).isEqualTo(80L);
        assertThatThrownBy(() -> credits.settle("diagnosis", 21)).isInstanceOf(RuntimeException.class);
    }

    @Test void holdsAndProtectionCannotBeOvercommittedByAdjustmentOrProtection() {
        long id = account("credits-invariants@example.test");
        credits.adjust(id, id, 100, "allocation", "grant-invariants");
        credits.reserve(id, "free-hold", "FREE", 60);
        assertThatThrownBy(() -> credits.protect(id, "DIAGNOSIS", 50)).isInstanceOf(RuntimeException.class);
        credits.protect(id, "DIAGNOSIS", 40);
        assertThatThrownBy(() -> credits.adjust(id, id, -1, "removal", "remove-invariants")).isInstanceOf(RuntimeException.class);
        credits.release("free-hold");
        credits.reserve(id, "free-after-release", "FREE", 60);
        credits.settle("free-after-release", 20);
        assertThat(db.queryForObject("SELECT balance FROM credit_accounts WHERE account_id = ?", Long.class, id)).isEqualTo(80L);
        credits.reserve(id, "remaining-free", "FREE", 40);
        assertThatThrownBy(() -> credits.reserve(id, "empty-free", "FREE", 1)).isInstanceOf(RuntimeException.class);
    }

    @Test void adjustmentsAreAuditedIdempotentAndImmutable() {
        long id = account("credits-audit@example.test");
        credits.adjust(id, id, 10, "support", "audit-grant");
        credits.adjust(id, id, 10, "support", "audit-grant");
        assertThat(db.queryForObject("SELECT balance FROM credit_accounts WHERE account_id = ?", Long.class, id)).isEqualTo(10L);
        assertThat(db.queryForObject("SELECT count(*) FROM credit_ledger WHERE request_id = 'audit-grant' AND operator_id = ? AND reason = 'support'", Integer.class, id)).isEqualTo(1);
        assertThatThrownBy(() -> credits.adjust(id, id, 11, "support", "audit-grant")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> db.update("DELETE FROM credit_ledger WHERE request_id = 'audit-grant'")).isInstanceOf(RuntimeException.class);
    }
    @Test void aiEntryRejectsEmptyBalanceAndSettlesOnlyOnce() {
        long id = account("credits-ai@example.test");
        assertThatThrownBy(() -> credits.reserveAi(id, "ai-empty", "FREE")).isInstanceOf(RuntimeException.class);
        credits.adjust(id, id, 5, "allocation", "ai-grant");
        credits.reserveAi(id, "ai-entry", "FREE");
        credits.settleAi("ai-entry");
        credits.settleAi("ai-entry");
        assertThat(db.queryForObject("SELECT count(*) FROM credit_ledger WHERE request_id = 'ai-entry'", Integer.class)).isEqualTo(1);
    }
    @Test void registrationReceivesConfiguredProvisionalTrial() {
        assertThat(db.queryForObject("SELECT initial_points FROM credit_allocation_config WHERE id = true", Long.class)).isEqualTo(50L);
    }
    @Test @org.springframework.transaction.annotation.Transactional
    void migrationAddsStageProtectionForExistingAccounts() throws Exception {
        db.execute("CREATE SCHEMA credit_legacy_test");
        db.execute("SET LOCAL search_path TO credit_legacy_test, public");
        db.execute("CREATE TABLE accounts (id bigint PRIMARY KEY)");
        db.execute("CREATE TABLE career_assessments (id bigint PRIMARY KEY, account_id bigint, status text)");
        db.execute("CREATE TABLE career_skill_sessions (account_id bigint, state jsonb)");
        db.update("INSERT INTO accounts(id) VALUES (42), (43), (44)");
        db.update("INSERT INTO career_assessments VALUES (1, 43, 'READY'), (2, 44, 'READY')");
        db.update("INSERT INTO career_skill_sessions VALUES (44, '{\"phase\":\"DONE\"}')");
        var migration = new org.springframework.core.io.ClassPathResource("db/migration/V14__credit_ledger.sql");
        db.execute(new String(migration.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        assertThat(db.queryForObject("SELECT count(*) FROM credit_stage_reserves WHERE account_id=42 AND points=10", Integer.class)).isEqualTo(2);
        assertThat(db.queryForObject("SELECT points FROM credit_stage_reserves WHERE account_id=43 AND stage='DIAGNOSIS'", Long.class)).isZero();
        assertThat(db.queryForObject("SELECT points FROM credit_stage_reserves WHERE account_id=43 AND stage='SKILLS'", Long.class)).isEqualTo(10L);
        assertThat(db.queryForObject("SELECT sum(points) FROM credit_stage_reserves WHERE account_id=44", Long.class)).isZero();
    }
    @Test void smallTopUpCanFundProtectedStagesWithoutFundingFreeChat() {
        long id = account("small-topup@example.test");
        db.update("INSERT INTO credit_stage_reserves VALUES (?, 'DIAGNOSIS', 10), (?, 'SKILLS', 10)", id, id);
        credits.adjust(id, id, 5, "test", "small-topup");
        assertThatThrownBy(() -> credits.reserve(id, "small-free", "FREE", 1)).isInstanceOf(RuntimeException.class);
        credits.reserve(id, "small-diagnosis", "DIAGNOSIS", 3);
        credits.reserve(id, "small-skills", "SKILLS", 2);
        assertThatThrownBy(() -> credits.reserve(id, "small-extra", "SKILLS", 1)).isInstanceOf(RuntimeException.class);
    }
}
