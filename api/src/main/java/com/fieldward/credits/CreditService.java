package com.fieldward.credits;

import java.util.List;
import java.util.Map;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CreditService {
    private final JdbcTemplate db;
    private final long aiPoints;
    private final ObjectProvider<ExpiredCreditCleanup> cleanup;
    public CreditService(JdbcTemplate db, @Value("${fieldward.credits.ai-points:1}") long aiPoints, ObjectProvider<ExpiredCreditCleanup> cleanup) {
        this.cleanup = cleanup;
        if (aiPoints <= 0) throw new IllegalArgumentException("AI credit cost must be positive");
        this.db = db;
        this.aiPoints = aiPoints;
    }
    @Transactional
    public Reservation reserveAi(long accountId, String requestId, String stage) {
        return reserve(accountId, requestId, stage, aiPoints);
    }
    @Transactional
    public LedgerEntry settleAi(String requestId) {
        Reservation reservation = find(requestId);
        if (reservation == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "预留不存在");
        return settle(requestId, reservation.maxPoints());
    }
    public record Reservation(String requestId, long accountId, String stage, long maxPoints, Long actualPoints, String status) {}
    public record LedgerEntry(String requestId, long accountId, String kind, long points) {}
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private long lock(long id) {
        try { return db.queryForObject("SELECT balance FROM credit_accounts WHERE account_id = ? FOR UPDATE", Long.class, id); }
        catch (EmptyResultDataAccessException e) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "账号不存在"); }
    }
    private Reservation find(String request) {
        return db.query("SELECT * FROM credit_reservations WHERE request_id = ?", (rs, n) -> new Reservation(rs.getString("request_id"), rs.getLong("account_id"), rs.getString("stage"), rs.getLong("max_points"), (Long) rs.getObject("actual_points"), rs.getString("status")), request).stream().findFirst().orElse(null);
    }
    private void valid(String s) { if (s == null || s.isBlank() || s.length() > 200) throw new IllegalArgumentException("invalid identifier"); }
    @Transactional
    public Reservation reserve(long accountId, String requestId, String stage, long maxPoints) {
        valid(requestId); valid(stage); if (maxPoints <= 0) throw new IllegalArgumentException("invalid points");
        long balance = lock(accountId);
        Reservation old = find(requestId);
        if (old != null) {
            if (old.accountId() != accountId || !old.stage().equals(stage) || old.maxPoints() != maxPoints) throw conflict("请求标识已使用");
            return old;
        }
        long held = db.queryForObject("SELECT COALESCE(sum(max_points), 0) FROM credit_reservations WHERE account_id = ? AND status = 'ACTIVE'", Long.class, accountId);
        long protectedPoints = db.queryForObject("SELECT COALESCE(sum(GREATEST(s.points - COALESCE((SELECT sum(r.max_points) FROM credit_reservations r WHERE r.account_id = s.account_id AND r.stage = s.stage AND r.status = 'ACTIVE'), 0), 0)), 0) FROM credit_stage_reserves s WHERE s.account_id = ? AND s.stage <> ?", Long.class, accountId, stage);
        long ownProtection = db.queryForObject("SELECT COALESCE((SELECT points FROM credit_stage_reserves WHERE account_id = ? AND stage = ?), 0)", Long.class, accountId, stage);
        // Underfunded stages share the available balance; unprotected chat has no such priority.
        protectedPoints = Math.min(protectedPoints, Math.max(balance - ownProtection, 0));
        if (maxPoints > balance - held - protectedPoints) throw conflict("积分不足");
        // Global request ids are serialized even when different accounts race.
        try { db.update("INSERT INTO credit_reservations(request_id, account_id, stage, max_points, status) VALUES (?, ?, ?, ?, 'ACTIVE')", requestId, accountId, stage, maxPoints); }
        catch (org.springframework.dao.DuplicateKeyException e) { throw conflict("请求标识已使用"); }
        return find(requestId);
    }
    @Transactional
    public LedgerEntry settle(String requestId, long actualPoints) {
        valid(requestId); if (actualPoints < 0) throw new IllegalArgumentException("invalid points");
        Reservation r = find(requestId); if (r == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "预留不存在");
        lock(r.accountId()); r = find(requestId);
        if (r.status().equals("SETTLED")) { if (r.actualPoints() != actualPoints) throw conflict("结算金额不一致"); return new LedgerEntry(requestId, r.accountId(), "CONSUME", -actualPoints); }
        if (!r.status().equals("ACTIVE") || actualPoints > r.maxPoints()) throw conflict("无法结算");
        db.update("UPDATE credit_accounts SET balance = balance - ? WHERE account_id = ?", actualPoints, r.accountId());
        db.update("UPDATE credit_stage_reserves SET points = GREATEST(points - ?, 0) WHERE account_id = ? AND stage = ?", actualPoints, r.accountId(), r.stage());
        db.update("UPDATE credit_reservations SET status = 'SETTLED', actual_points = ? WHERE request_id = ?", actualPoints, requestId);
        db.update("INSERT INTO credit_ledger(account_id, request_id, kind, points) VALUES (?, ?, 'CONSUME', ?)", r.accountId(), requestId, -actualPoints);
        return new LedgerEntry(requestId, r.accountId(), "CONSUME", -actualPoints);
    }
    @Transactional
    public void releaseIfPresent(String requestId) {
        if (find(requestId) != null) release(requestId);
    }
    @Transactional
    public void release(String requestId) {
        valid(requestId); Reservation r = find(requestId); if (r == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "预留不存在");
        lock(r.accountId()); db.update("UPDATE credit_reservations SET status = 'RELEASED' WHERE request_id = ? AND status = 'ACTIVE'", requestId);
    }
    @Transactional
    public void adjust(long accountId, long operatorId, long points, String reason, String requestId) {
        cleanup.getObject().recover(accountId);
        valid(requestId); valid(reason); if (points == 0) throw new IllegalArgumentException("invalid points");
        long balance = lock(accountId);
        var existing = db.queryForList("SELECT account_id, operator_id, points, reason FROM credit_ledger WHERE request_id = ?", requestId);
        if (!existing.isEmpty()) { var row = existing.get(0); if (((Number)row.get("account_id")).longValue() != accountId || (row.get("operator_id") == null || ((Number)row.get("operator_id")).longValue() != operatorId) || ((Number)row.get("points")).longValue() != points || !reason.equals(row.get("reason"))) throw conflict("请求标识已使用"); return; }
        long held = db.queryForObject("SELECT COALESCE(sum(max_points), 0) FROM credit_reservations WHERE account_id = ? AND status = 'ACTIVE'", Long.class, accountId);
        long protectedPoints = db.queryForObject("SELECT COALESCE(sum(points), 0) FROM credit_stage_reserves WHERE account_id = ?", Long.class, accountId);
        long overlapping = db.queryForObject("SELECT COALESCE(sum(LEAST(s.points, COALESCE((SELECT sum(r.max_points) FROM credit_reservations r WHERE r.account_id = s.account_id AND r.stage = s.stage AND r.status = 'ACTIVE'), 0))), 0) FROM credit_stage_reserves s WHERE s.account_id = ?", Long.class, accountId);
        if (points < 0 && (points < -balance || balance + points < held + protectedPoints - overlapping)) throw conflict("积分已预留");
        db.update("UPDATE credit_accounts SET balance = balance + ? WHERE account_id = ?", points, accountId);
        db.update("INSERT INTO credit_ledger(account_id, request_id, kind, points, operator_id, reason) VALUES (?, ?, 'ADJUST', ?, ?, ?)", accountId, requestId, points, operatorId, reason);
    }
    @Transactional
    public void protect(long accountId, String stage, long points) {
        valid(stage); if (points < 0) throw new IllegalArgumentException("invalid points"); long balance = lock(accountId);
        long others = db.queryForObject("SELECT COALESCE(sum(points), 0) FROM credit_stage_reserves WHERE account_id = ? AND stage <> ?", Long.class, accountId, stage);
        long held = db.queryForObject("SELECT COALESCE(sum(max_points), 0) FROM credit_reservations WHERE account_id = ? AND status = 'ACTIVE'", Long.class, accountId);
        long overlapOther = db.queryForObject("SELECT COALESCE(sum(LEAST(s.points, COALESCE((SELECT sum(r.max_points) FROM credit_reservations r WHERE r.account_id = s.account_id AND r.stage = s.stage AND r.status = 'ACTIVE'), 0))), 0) FROM credit_stage_reserves s WHERE s.account_id = ? AND s.stage <> ?", Long.class, accountId, stage);
        long overlapStage = db.queryForObject("SELECT COALESCE(sum(max_points), 0) FROM credit_reservations WHERE account_id = ? AND stage = ? AND status = 'ACTIVE'", Long.class, accountId, stage);
        Long current = db.queryForObject("SELECT COALESCE((SELECT points FROM credit_stage_reserves WHERE account_id = ? AND stage = ?), 0)", Long.class, accountId, stage);
        if (points > current && held + others - overlapOther + Math.max(points - overlapStage, 0) > balance) throw conflict("积分不足");
        db.update("INSERT INTO credit_stage_reserves VALUES (?, ?, ?) ON CONFLICT(account_id, stage) DO UPDATE SET points = EXCLUDED.points", accountId, stage, points);
    }
    @Transactional(readOnly = true)
    public Map<String, Object> view(long accountId) {
        cleanup.getObject().recover(accountId);
        Long balance = db.queryForObject("SELECT balance FROM credit_accounts WHERE account_id = ?", Long.class, accountId);
        List<Map<String,Object>> stages = db.queryForList("SELECT stage, points FROM credit_stage_reserves WHERE account_id = ? ORDER BY stage", accountId);
        List<Map<String,Object>> ledger = db.queryForList("SELECT request_id, kind, points, reason, created_at FROM credit_ledger WHERE account_id = ? ORDER BY id DESC", accountId);
        return Map.of("balance", balance, "stages", stages, "ledger", ledger, "rules", "当前积分及消耗为暂定试用值，待成本测算后调整");
    }
}
