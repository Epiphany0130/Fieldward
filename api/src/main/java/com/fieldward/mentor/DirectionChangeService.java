package com.fieldward.mentor;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class DirectionChangeService {
    private final JdbcTemplate db;
    DirectionChangeService(JdbcTemplate db) { this.db = db; }
    record Proposal(UUID id, String introduction, List<String> shared, List<String> added, List<String> removed, String advice) {}
    record DirectionView(String directionId) {}
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "方向变更不存在"); }
    private ResponseStatusException conflict() { return new ResponseStatusException(HttpStatus.CONFLICT, "当前方向已变更，请重新比较"); }
    private void lock(long account) {
        if (db.query("SELECT id FROM accounts WHERE id=? FOR UPDATE", (r,n)->r.getLong(1), account).isEmpty()) throw missing();
    }
    private List<String> route(String direction) {
        return db.queryForList("SELECT capability_id FROM career_route_items WHERE direction_id=? AND version=(SELECT max(version) FROM career_route_versions WHERE direction_id=?) ORDER BY position", String.class, direction, direction);
    }
    private String title(String id) { return db.queryForObject("SELECT title FROM career_capabilities WHERE id=?", String.class, id); }
    @Transactional
    public Proposal propose(long account, String direction) {
        CareerCatalog.require(direction); // Only directions supported by the legacy assessment can be selected.
        String description = db.query("SELECT description FROM career_directions WHERE id=?", (rs, row) -> rs.getString(1), direction).stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择一个有效的职业方向"));
        lock(account);
        var current = db.query("SELECT a.id,a.direction_id FROM career_targets t JOIN career_assessments a ON a.id=t.assessment_id AND a.account_id=t.account_id WHERE t.account_id=?", (r,n)->new Object[]{r.getLong(1),r.getString(2)}, account).stream().findFirst().orElseThrow(this::missing);
        String from = (String) current[1];
        if (from.equals(direction)) throw conflict();
        var old = route(from); var next = route(direction);
        UUID id = UUID.randomUUID();
        db.update("INSERT INTO direction_change_proposals(id,account_id,old_assessment_id,direction_id) VALUES (?,?,?,?)", id, account, current[0], direction);
        return new Proposal(id, description, next.stream().filter(old::contains).map(this::title).toList(), next.stream().filter(x -> !old.contains(x)).map(this::title).toList(), old.stream().filter(x -> !next.contains(x)).map(this::title).toList(), "共同能力证据会保留；新方向的其他能力需要补充诊断。请确认是否切换。 ");
    }
    @Transactional
    public DirectionView confirmDirectionChange(long account, UUID id) {
        lock(account);
        var row = db.query("SELECT old_assessment_id,direction_id,confirmed_assessment_id FROM direction_change_proposals WHERE id=? AND account_id=? FOR UPDATE", (r,n)->new Object[]{r.getLong(1),r.getString(2),r.getObject(3, Long.class)}, id, account).stream().findFirst().orElseThrow(this::missing);
        String direction = (String)row[1];
        Long active = db.query("SELECT assessment_id FROM career_targets WHERE account_id=?", (r,n)->r.getLong(1), account).stream().findFirst().orElseThrow(this::missing);
        if (row[2] != null) {
            String currentDirection = db.queryForObject("SELECT direction_id FROM career_assessments WHERE account_id=? AND id=?", String.class, account, active);
            if (!direction.equals(currentDirection)) throw conflict();
            return new DirectionView(direction);
        }
        if (!active.equals(row[0])) throw conflict();
        Long version = db.queryForObject("SELECT max(version) FROM resume_versions WHERE account_id=?", Long.class, account);
        String digest = id.toString().replace("-", "") + "0".repeat(32);
        Long assessment = db.queryForObject("INSERT INTO career_assessments(account_id,resume_version,direction_id,source_digest,status,attempt) VALUES (?,?,?,?,'WAITING_TEXT',1) RETURNING id", Long.class, account, version, direction, digest);
        db.update("UPDATE career_targets SET assessment_id=? WHERE account_id=?", assessment, account);
        db.update("UPDATE direction_change_proposals SET confirmed_assessment_id=? WHERE id=? AND account_id=?", assessment, id, account);
        return new DirectionView(direction);
    }
}
