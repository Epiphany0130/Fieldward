package com.fieldward.credits;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Recovers only operations whose database lease has expired. */
@Service
public class ExpiredCreditCleanup {
    private final JdbcTemplate db;
    private final CreditService credits;

    public ExpiredCreditCleanup(JdbcTemplate db, CreditService credits) {
        this.db = db;
        this.credits = credits;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recover(long accountId) {
        var turns = db.query("""
            UPDATE mentor_turns t SET status='INTERRUPTED', lease_until=NULL, updated_at=now()
            FROM mentor_main_conversations c
            WHERE t.conversation_id=c.id AND c.account_id=? AND t.status='STREAMING' AND t.lease_until<=now()
            RETURNING t.id, t.attempt
            """, (rs, n) -> "mentor:" + rs.getLong(1) + ":" + rs.getInt(2), accountId);
        var assessments = db.query("""
            UPDATE career_assessments SET status='FAILED', lease_until=NULL, updated_at=now()
            WHERE account_id=? AND status='ANALYZING' AND lease_until<=now()
            RETURNING id, attempt
            """, (rs, n) -> "career:" + rs.getLong(1) + ":" + rs.getInt(2), accountId);
        var claims = db.query("""
            SELECT claim_id::text FROM career_skill_sessions
            WHERE account_id=? AND claim_id IS NOT NULL AND lease_until<=now() FOR UPDATE
            """, (rs, n) -> rs.getString(1), accountId);
        db.update("UPDATE career_skill_sessions SET claim_id=NULL, lease_until=NULL WHERE account_id=? AND claim_id IS NOT NULL AND lease_until<=now()", accountId);
        var learning = db.query("""
            SELECT claim_id FROM learning_verifications WHERE account_id=? AND claim_id IS NOT NULL AND lease_until<=now() FOR UPDATE
            """, (rs, n) -> rs.getString(1), accountId);
        db.update("UPDATE learning_verifications SET claim_id=NULL, lease_until=NULL WHERE account_id=? AND claim_id IS NOT NULL AND lease_until<=now()", accountId);
        for (var claim : learning) credits.releaseIfPresent("learning:" + claim);
        for (var request : turns) credits.releaseIfPresent(request);
        for (var request : assessments) credits.releaseIfPresent(request);
        for (var claim : claims) credits.releaseIfPresent("skill:" + claim);
    }
}
