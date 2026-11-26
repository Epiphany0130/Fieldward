package com.fieldward.realtime;

import com.fieldward.credits.CreditService;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RealtimeSessionService {
    private final JdbcTemplate db;
    private final CreditService credits;
    public RealtimeSessionService(JdbcTemplate db, CreditService credits) { this.db = db; this.credits = credits; }
    public record SessionView(UUID id, String purpose, String mode, String status, int revision, String summary) {}
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private void valid(String value) { if (value == null || value.isBlank() || value.length() > 150) throw new IllegalArgumentException("invalid request"); }
    private SessionView read(long accountId, UUID id, boolean lock) {
        return db.query("SELECT id, purpose, mode, status, revision, summary FROM realtime_sessions WHERE id = ? AND account_id = ?" + (lock ? " FOR UPDATE" : ""),
            (rs, n) -> new SessionView(rs.getObject("id", UUID.class), rs.getString("purpose"), rs.getString("mode"), rs.getString("status"), rs.getInt("revision"), rs.getString("summary")), id, accountId)
            .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在"));
    }
    @Transactional(readOnly = true)
    public SessionView get(long accountId, UUID id) { return read(accountId, id, false); }
    @Transactional
    public SessionView start(long accountId, String purpose, String mode, String requestId) {
        valid(requestId);
        if (!Set.of("DIAGNOSIS", "INTERVIEW", "SKILLS").contains(purpose) || !Set.of("PHONE", "VIDEO").contains(mode)) throw new IllegalArgumentException("invalid session");
        db.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", Object.class, "realtime-start:" + requestId);
        var existing = db.queryForList("SELECT id, account_id, purpose, mode FROM realtime_sessions WHERE start_request = ?", requestId);
        if (!existing.isEmpty()) {
            var row = existing.get(0);
            if (((Number) row.get("account_id")).longValue() != accountId || !purpose.equals(row.get("purpose")) || !mode.equals(row.get("mode"))) throw conflict("请求标识已使用");
            return read(accountId, (UUID) row.get("id"), false);
        }
        UUID id = UUID.randomUUID();
        db.update("INSERT INTO realtime_sessions(id, account_id, start_request, purpose, mode, status) VALUES (?, ?, ?, ?, ?, 'ACTIVE')", id, accountId, requestId, purpose, mode);
        credits.reserveAi(accountId, "realtime:" + id + ":0", purpose);
        return read(accountId, id, false);
    }
    @Transactional
    public SessionView checkpoint(long accountId, UUID id, int revision, String summary) {
        if (summary == null || summary.length() > 600) throw new IllegalArgumentException("invalid summary");
        var current = read(accountId, id, true);
        if (!current.status().equals("ACTIVE")) throw conflict("会话未进行");
        if (revision == current.revision() && summary.equals(current.summary())) return current;
        if (revision != current.revision() + 1) throw conflict("修订号不匹配");
        db.update("UPDATE realtime_sessions SET revision = ?, summary = ?, updated_at = now() WHERE id = ?", revision, summary, id);
        return read(accountId, id, false);
    }
    @Transactional
    public SessionView segment(long accountId, UUID id, String eventId, int segment) {
        valid(eventId);
        db.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", Object.class, "realtime-event:" + eventId);
        var current = read(accountId, id, true);
        var existing = db.queryForList("SELECT session_id, segment FROM realtime_events WHERE event_id = ?", eventId);
        if (!existing.isEmpty()) {
            var row = existing.get(0);
            if (!id.equals(row.get("session_id")) || segment != ((Number) row.get("segment")).intValue()) throw conflict("事件标识已使用");
            return current;
        }
        int expected = db.queryForObject("SELECT segment FROM realtime_sessions WHERE id = ?", Integer.class, id);
        if (!current.status().equals("ACTIVE") || segment != expected) throw conflict("事件顺序不匹配");
        credits.settleAi("realtime:" + id + ":" + segment);
        db.update("INSERT INTO realtime_events(event_id, session_id, segment) VALUES (?, ?, ?)", eventId, id, segment);
        db.update("UPDATE realtime_sessions SET status = 'PAUSED', segment = segment + 1, updated_at = now() WHERE id = ?", id);
        return read(accountId, id, false);
    }
    @Transactional
    public SessionView resume(long accountId, UUID id) {
        var current = read(accountId, id, true);
        if (!current.status().equals("PAUSED")) throw conflict("会话无法恢复");
        int segment = db.queryForObject("SELECT segment FROM realtime_sessions WHERE id = ?", Integer.class, id);
        credits.reserveAi(accountId, "realtime:" + id + ":" + segment, current.purpose());
        db.update("UPDATE realtime_sessions SET status = 'ACTIVE', updated_at = now() WHERE id = ?", id);
        return read(accountId, id, false);
    }
    @Transactional
    public void expire() {
        var expired = db.queryForList("SELECT id, segment FROM realtime_sessions WHERE status = 'ACTIVE' AND updated_at < now() - interval '30 minutes' FOR UPDATE SKIP LOCKED");
        for (var row : expired) {
            UUID id = (UUID) row.get("id");
            int segment = ((Number) row.get("segment")).intValue();
            credits.releaseIfPresent("realtime:" + id + ":" + segment);
            db.update("UPDATE realtime_sessions SET status = 'PAUSED', segment = segment + 1, updated_at = now() WHERE id = ?", id);
        }
    }
    @Transactional
    public SessionView finish(long accountId, UUID id, String requestId) {
        valid(requestId);
        var current = read(accountId, id, true);
        if (current.status().equals("COMPLETED")) {
            String previous = db.queryForObject("SELECT finish_request FROM realtime_sessions WHERE id = ?", String.class, id);
            if (!requestId.equals(previous)) throw conflict("会话已结束");
            return current;
        }
        if (current.status().equals("ACTIVE")) {
            int segment = db.queryForObject("SELECT segment FROM realtime_sessions WHERE id = ?", Integer.class, id);
            credits.settleAi("realtime:" + id + ":" + segment);
        }
        db.update("UPDATE realtime_sessions SET status = 'COMPLETED', finish_request = ?, updated_at = now() WHERE id = ?", requestId, id);
        return read(accountId, id, false);
    }
}
