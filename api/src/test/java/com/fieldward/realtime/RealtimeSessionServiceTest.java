package com.fieldward.realtime;

import com.fieldward.PostgresIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

class RealtimeSessionServiceTest extends PostgresIntegrationTest {
    @Autowired RealtimeSessionService sessions;
    @Autowired JdbcTemplate db;
    @Autowired com.fieldward.credits.CreditService credits;

    private long account() {
        return db.queryForObject("INSERT INTO accounts(email, display_name, password_hash, role) VALUES (?, 'Test', 'hash', 'STUDENT') RETURNING id", Long.class, UUID.randomUUID() + "@example.test");
    }

    @Test void checkpointIsAccountScopedAndRevisionOrdered() {
        long owner = account(), other = account();
        var started = sessions.start(owner, "DIAGNOSIS", "PHONE", UUID.randomUUID().toString());
        var saved = sessions.checkpoint(owner, started.id(), 1, "关键进度");
        assertThat(saved.revision()).isEqualTo(1);
        assertThat(sessions.get(owner, started.id()).summary()).isEqualTo("关键进度");
        assertThatThrownBy(() -> sessions.get(other, started.id())).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> sessions.checkpoint(owner, started.id(), 3, "乱序")).isInstanceOf(RuntimeException.class);
        assertThat(sessions.checkpoint(owner, started.id(), 1, "关键进度")).isEqualTo(saved);
        assertThatThrownBy(() -> sessions.checkpoint(owner, started.id(), 1, "不同内容")).isInstanceOf(RuntimeException.class);
    }

    @Test void checkpointRejectsOversizedTranscript() {
        long owner = account();
        var started = sessions.start(owner, "DIAGNOSIS", "PHONE", UUID.randomUUID().toString());
        assertThatThrownBy(() -> sessions.checkpoint(owner, started.id(), 1, "transcript ".repeat(100))).isInstanceOf(RuntimeException.class);
        assertThat(sessions.get(owner, started.id()).summary()).isEmpty();
    }

    @Test void segmentEventsAreIdempotentAndOrdered() {
        long owner = account();
        var started = sessions.start(owner, "DIAGNOSIS", "PHONE", UUID.randomUUID().toString());
        var advanced = sessions.segment(owner, started.id(), "event-" + started.id(), 0);
        assertThat(sessions.segment(owner, started.id(), "event-" + started.id(), 0)).isEqualTo(advanced);
        assertThatThrownBy(() -> sessions.segment(owner, started.id(), "out-of-order-" + started.id(), 2)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> sessions.segment(account(), started.id(), "other-" + started.id(), 1)).isInstanceOf(RuntimeException.class);
        assertThat(db.queryForObject("SELECT count(*) FROM credit_ledger WHERE request_id = ?", Integer.class, "realtime:" + started.id() + ":0")).isEqualTo(1);
    }

    @Test void eventIdCollisionAcrossSessionsIsAConflict() throws Exception {
        long owner = account();
        var first = sessions.start(owner, "DIAGNOSIS", "PHONE", UUID.randomUUID().toString());
        var second = sessions.start(owner, "DIAGNOSIS", "PHONE", UUID.randomUUID().toString());
        String event = UUID.randomUUID().toString();
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        var gate = new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Object> a = () -> { gate.await(); try { return sessions.segment(owner, first.id(), event, 0); } catch (Exception e) { return e; } };
            java.util.concurrent.Callable<Object> b = () -> { gate.await(); try { return sessions.segment(owner, second.id(), event, 0); } catch (Exception e) { return e; } };
            var one = executor.submit(a); var two = executor.submit(b); gate.countDown();
            var outcomes = java.util.List.of(one.get(), two.get());
            assertThat(outcomes.stream().filter(x -> x instanceof RealtimeSessionService.SessionView).count()).isEqualTo(1);
            assertThat(outcomes.stream().filter(x -> x instanceof org.springframework.web.server.ResponseStatusException).count()).isEqualTo(1);
        } finally { executor.shutdownNow(); }
    }

    @Test void savedStateAndEventsSurviveServiceReconstruction() {
        long owner = account();
        var started = sessions.start(owner, "DIAGNOSIS", "PHONE", UUID.randomUUID().toString());
        sessions.checkpoint(owner, started.id(), 1, "必要要点");
        String event = UUID.randomUUID().toString();
        sessions.segment(owner, started.id(), event, 0);
        var restarted = new RealtimeSessionService(db, credits);
        assertThat(restarted.get(owner, started.id()).summary()).isEqualTo("必要要点");
        assertThat(restarted.segment(owner, started.id(), event, 0).status()).isEqualTo("PAUSED");
        assertThat(sessions.resume(owner, started.id()).status()).isEqualTo("ACTIVE");
        assertThat(sessions.segment(owner, started.id(), UUID.randomUUID().toString(), 1).status()).isEqualTo("PAUSED");
        assertThat(db.queryForObject("SELECT count(*) FROM credit_ledger WHERE request_id LIKE ?", Integer.class, "realtime:" + started.id() + ":%")).isEqualTo(2);
    }

    @Test void expiredSessionPausesAndReleasesReservation() {
        long owner = account();
        var started = sessions.start(owner, "DIAGNOSIS", "PHONE", UUID.randomUUID().toString());
        db.update("UPDATE realtime_sessions SET updated_at = now() - interval '2 hours' WHERE id = ?", started.id());
        sessions.expire();
        assertThat(sessions.get(owner, started.id()).status()).isEqualTo("PAUSED");
        assertThat(db.queryForObject("SELECT status FROM credit_reservations WHERE request_id = ?", String.class, "realtime:" + started.id() + ":0")).isEqualTo("RELEASED");
    }

    @Test void insufficientBalanceDoesNotCreateSession() {
        long owner = account();
        db.update("UPDATE credit_accounts SET balance = 0 WHERE account_id = ?", owner);
        String request = UUID.randomUUID().toString();
        assertThatThrownBy(() -> sessions.start(owner, "DIAGNOSIS", "PHONE", request)).isInstanceOf(RuntimeException.class);
        assertThat(db.queryForObject("SELECT count(*) FROM realtime_sessions WHERE start_request = ?", Integer.class, request)).isZero();
    }

    @Test void startRetriesDoNotReserveTwiceAndRejectChangedInputs() {
        long owner = account();
        String request = UUID.randomUUID().toString();
        var first = sessions.start(owner, "DIAGNOSIS", "PHONE", request);
        assertThat(sessions.start(owner, "DIAGNOSIS", "PHONE", request)).isEqualTo(first);
        assertThatThrownBy(() -> sessions.start(owner, "DIAGNOSIS", "VIDEO", request)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> sessions.start(account(), "DIAGNOSIS", "PHONE", request)).isInstanceOf(RuntimeException.class);
        assertThat(db.queryForObject("SELECT count(*) FROM credit_reservations WHERE request_id = ?", Integer.class, "realtime:" + first.id() + ":0")).isEqualTo(1);
    }

    @Test void finishIsIdempotentAndSettlesOnlyOnce() {
        long owner = account();
        String request = UUID.randomUUID().toString();
        var started = sessions.start(owner, "DIAGNOSIS", "VIDEO", request);
        var done = sessions.finish(owner, started.id(), "finish-" + request);
        assertThat(done.status()).isEqualTo("COMPLETED");
        assertThat(sessions.finish(owner, started.id(), "finish-" + request)).isEqualTo(done);
        assertThat(db.queryForObject("SELECT count(*) FROM credit_ledger WHERE request_id = ?", Integer.class, "realtime:" + started.id() + ":0")).isEqualTo(1);
        assertThatThrownBy(() -> sessions.finish(account(), started.id(), "finish-" + request)).isInstanceOf(RuntimeException.class);
    }
}
