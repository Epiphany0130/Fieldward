package com.fieldward.resume;

import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Processes committed verification evidence; each update and its completion marker are atomic. */
@Component
class ResumeEvidenceWorker {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(ResumeEvidenceWorker.class);
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final ResumeUpdateService updates;
    record Job(long account, long evidence, String request) {}

    ResumeEvidenceWorker(JdbcTemplate db, TransactionTemplate tx, ResumeUpdateService updates) {
        this.db = db; this.tx = tx; this.updates = updates;
    }

    @Scheduled(initialDelayString = "${fieldward.resume.evidence-initial-delay-ms:10000}", fixedDelayString = "${fieldward.resume.evidence-interval-ms:10000}")
    void process() {
        for (int i = 0; i < 20 && processOne(); i++) { /* bound work per scheduled run */ }
    }

    private boolean processOne() {
        Job[] claimed = new Job[1];
        try {
            tx.executeWithoutResult(status -> {
                var rows = db.query("SELECT account_id,evidence_id,request_id::text FROM resume_evidence_outbox WHERE completed_at IS NULL AND next_attempt_at<=now() ORDER BY next_attempt_at,evidence_id FOR UPDATE SKIP LOCKED LIMIT 1", (rs,n) -> new Job(rs.getLong(1),rs.getLong(2),rs.getString(3)));
                if (rows.isEmpty()) return;
                var job = rows.getFirst(); claimed[0] = job;
                db.queryForList("SELECT id FROM accounts WHERE id=? FOR NO KEY UPDATE", job.account());
                Long latest = db.query("SELECT max(version) FROM resume_versions WHERE account_id=?", rs -> rs.next() ? (Long) rs.getObject(1) : null, job.account());
                if (latest == null) throw new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "请先创建简历");
                Boolean alreadyApplied = db.queryForObject("SELECT EXISTS(SELECT 1 FROM resume_evidence_updates WHERE account_id=? AND evidence_id=?)", Boolean.class, job.account(), job.evidence());
                if (!Boolean.TRUE.equals(alreadyApplied)) {
                    // Lock the draft before comparing user content.
                    var drafts = db.query("SELECT content::text FROM resume_drafts WHERE account_id=? FOR UPDATE", (rs,n) -> rs.getString(1), job.account());
                    if (!drafts.isEmpty()) {
                        Boolean differs = db.queryForObject("SELECT content IS DISTINCT FROM CAST(? AS jsonb) FROM resume_versions WHERE account_id=? AND version=?", Boolean.class, drafts.getFirst(), job.account(), latest);
                        if (Boolean.TRUE.equals(differs)) throw new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "草稿有尚未提交的修改，请先提交或处理草稿");
                    }
                    updates.applyEvidence(job.account(), latest, job.evidence(), job.request());
                }
                db.update("UPDATE resume_evidence_outbox SET completed_at=now() WHERE account_id=? AND evidence_id=?", job.account(), job.evidence());
            });
        } catch (Exception ex) {
            Job job = claimed[0];
            if (job == null) { log.warn("Unable to claim resume evidence update", ex); return false; }
            String reason = ex instanceof ResponseStatusException conflict ? conflict.getReason() : "系统暂时无法处理";
            if (!(ex instanceof ResponseStatusException)) log.warn("Resume evidence update failed; it will be retried", ex);
            tx.executeWithoutResult(status -> {
                var rows = db.query("SELECT notified_at IS NULL FROM resume_evidence_outbox WHERE account_id=? AND evidence_id=? AND completed_at IS NULL AND next_attempt_at<=now() FOR UPDATE", (rs,n) -> rs.getBoolean(1), job.account(), job.evidence());
                if (rows.isEmpty()) return;
                db.update("UPDATE resume_evidence_outbox SET next_attempt_at=now()+interval '1 minute', notified_at=COALESCE(notified_at,now()) WHERE account_id=? AND evidence_id=?", job.account(), job.evidence());
                if (rows.getFirst()) db.update("INSERT INTO mentor_turns(conversation_id,request_id,student_message,mentor_reply,status,attempt) SELECT id,gen_random_uuid(),NULL,?,'COMPLETED',1 FROM mentor_main_conversations WHERE account_id=?", "学习验证已通过，简历暂未更新：" + reason + "。处理后系统会自动重试。", job.account());
            });
        }
        return claimed[0] != null;
    }
}
