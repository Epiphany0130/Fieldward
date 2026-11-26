package com.fieldward.mentor;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/** Stores only the final report and answer highlights, never a transcript. */
@Service
class DiagnosisReports {
    private final JdbcTemplate db;
    private final CapabilityService capabilities;
    private final JsonMapper json;
    DiagnosisReports(JdbcTemplate db, CapabilityService capabilities, JsonMapper json) { this.db=db; this.capabilities=capabilities; this.json=json; }
    record Finding(String capabilityId, String outcome, String highlight) {}
    record Input(String directionId, String summary, List<Finding> findings, String presentationAdvice) {}
    record Report(UUID sessionId, String directionId, String summary, List<Finding> findings, List<String> pending, List<String> routeTasks, String presentation) {}
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "诊断报告不存在"); }
    private ResponseStatusException conflict() { return new ResponseStatusException(HttpStatus.CONFLICT, "诊断报告已存在或会话未完成"); }
    @Transactional
    public void bind(long account, UUID session, String direction) {
        var row = db.query("SELECT purpose,status FROM realtime_sessions WHERE id=? AND account_id=? FOR UPDATE", (r,n)->new String[]{r.getString(1),r.getString(2)}, session, account).stream().findFirst().orElseThrow(this::missing);
        var bound = db.query("SELECT direction_id FROM diagnosis_session_context WHERE session_id=?", (r,n)->r.getString(1), session);
        if (!bound.isEmpty()) {
            if (!bound.getFirst().equals(direction)) throw conflict();
            return;
        }
        if (!"DIAGNOSIS".equals(row[0]) || !"ACTIVE".equals(row[1])) throw conflict();
        var current = db.query("SELECT a.direction_id FROM career_targets t JOIN career_assessments a ON a.id=t.assessment_id AND a.account_id=t.account_id WHERE t.account_id=?", (r,n)->r.getString(1), account).stream().findFirst().orElseThrow(this::missing);
        if (!current.equals(direction)) throw conflict();
        db.update("INSERT INTO diagnosis_session_context(session_id,direction_id) VALUES (?,?)", session, direction);
    }
    @Transactional(readOnly=true)
    public boolean hasCompleted(long account) { return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM diagnosis_reports WHERE account_id=?)", Boolean.class, account)); }
    @Transactional(readOnly=true)
    public Report get(long account, UUID session) {
        return db.query("SELECT report::text FROM diagnosis_reports WHERE account_id=? AND session_id=?", (r,n)->json.readValue(r.getString(1), Report.class), account, session).stream().findFirst().orElseThrow(this::missing);
    }
    @Transactional
    public Report complete(long account, UUID session, String request, Input input) {
        if (request == null || request.isBlank() || request.length()>150 || input == null || input.directionId()==null || input.summary()==null || input.summary().length()>2000 || input.findings()==null || input.findings().size()>100 || (input.presentationAdvice()!=null && input.presentationAdvice().length()>500)) throw new IllegalArgumentException("invalid report");
        var row = db.query("SELECT purpose,mode,status FROM realtime_sessions WHERE id=? AND account_id=? FOR UPDATE", (r,n)->new String[]{r.getString(1),r.getString(2),r.getString(3)}, session, account).stream().findFirst().orElseThrow(this::missing);
        var existing = db.query("SELECT request_id,input::text FROM diagnosis_reports WHERE session_id=? AND account_id=?", (r,n)->new String[]{r.getString(1),r.getString(2)}, session, account);
        if (!existing.isEmpty()) {
            if (!request.equals(existing.getFirst()[0]) || !json.readTree(existing.getFirst()[1]).equals(json.valueToTree(input))) throw conflict();
            return get(account, session);
        }
        if (!"DIAGNOSIS".equals(row[0]) || !"COMPLETED".equals(row[2])) throw conflict();
        var direction = db.query("SELECT direction_id FROM diagnosis_session_context WHERE session_id=?", (r,n)->r.getString(1), session).stream().findFirst().orElseThrow(this::conflict);
        if (!direction.equals(input.directionId())) throw conflict();
        var route = capabilities.currentRoute(account, input.directionId());
        var ids = route.capabilities().stream().map(CapabilityService.RouteCapability::capabilityId).toList();
        if (input.findings().stream().anyMatch(f -> f==null || !ids.contains(f.capabilityId()) || !Set.of("PASSED","NEEDS_PRACTICE","PENDING").contains(f.outcome()) || f.highlight()==null || f.highlight().length()>500) || input.findings().stream().map(Finding::capabilityId).distinct().count()!=input.findings().size()) throw new IllegalArgumentException("invalid findings");
        for (var finding : input.findings()) capabilities.recordEvidence(account, finding.capabilityId(), new CapabilityService.EvidenceInput("DIAGNOSIS", session.toString(), finding.outcome()));
        var assessed = input.findings().stream().filter(f -> !f.outcome().equals("PENDING")).map(Finding::capabilityId).toList();
        var pending = ids.stream().filter(id -> !assessed.contains(id)).toList();
        var tasks = route.capabilities().stream().filter(c -> input.findings().stream().noneMatch(f -> f.capabilityId().equals(c.capabilityId()) && f.outcome().equals("PASSED"))).map(CapabilityService.RouteCapability::objective).toList();
        String presentation = "PHONE".equals(row[1]) ? "未评估" : input.presentationAdvice();
        if (presentation == null || presentation.isBlank()) presentation = "暂无建议";
        var report = new Report(session,input.directionId(),input.summary(),List.copyOf(input.findings()),pending,tasks,presentation);
        db.update("INSERT INTO diagnosis_reports(session_id,account_id,request_id,input,report) VALUES (?,?,?,CAST(? AS jsonb),CAST(? AS jsonb))", session, account, request, json.writeValueAsString(input), json.writeValueAsString(report));
        return report;
    }
}
