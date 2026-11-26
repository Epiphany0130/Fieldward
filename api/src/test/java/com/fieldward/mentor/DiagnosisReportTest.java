package com.fieldward.mentor;

import com.fieldward.PostgresIntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

class DiagnosisReportTest extends PostgresIntegrationTest {
    @Autowired JdbcTemplate db;
    @Autowired DiagnosisReports reports;
    @Autowired CapabilityService capabilities;
    @Autowired CareerPlans plans;
    @Autowired org.apache.ibatis.session.SqlSession sqlSession;
    @Test @Transactional
    void completedReportIsPrivateIdempotentAndOnlyMarksSupportedCapabilities() {
        long owner = account("report-owner"), other = account("report-other");
        db.update("INSERT INTO resume_versions(account_id,version,draft_revision,content) VALUES (?,1,1,'{}')", owner);
        long assessment = db.queryForObject("INSERT INTO career_assessments(account_id,resume_version,direction_id,source_digest,status,attempt) VALUES (?,1,'java-backend',?,'WAITING_TEXT',1) RETURNING id", Long.class, owner, "a".repeat(64));
        db.update("INSERT INTO career_targets(account_id,assessment_id) VALUES (?,?)", owner, assessment);
        db.update("UPDATE career_assessments SET status='READY', report=CAST(? AS jsonb) WHERE id=?", "{\"matchPercent\":80,\"stage\":\"INTERVIEW_READY\",\"interviewThreshold\":60,\"coreCovered\":true,\"sourceQuality\":\"READABLE\",\"skills\":[],\"roadmap\":[],\"analysisVersion\":4,\"topicSuggestions\":{}}", assessment);
        assertThat(plans.current(owner).report().matchPercent()).isNull();
        assertThat(plans.current(owner).report().stage()).isEqualTo(CareerFit.Stage.INTERVIEW_READY);
        UUID session = UUID.randomUUID();
        db.update("INSERT INTO realtime_sessions(id,account_id,start_request,purpose,mode,status,finish_request) VALUES (?,?,?,'DIAGNOSIS','PHONE','ACTIVE',NULL)", session, owner, session.toString());
        reports.bind(owner, session, "java-backend");
        db.update("UPDATE realtime_sessions SET status='COMPLETED', finish_request='finish' WHERE id=?", session);
        assertThatThrownBy(() -> reports.bind(other, session, "data-engineering")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> reports.complete(owner, session, "wrong", new DiagnosisReports.Input("data-engineering", "总结", List.of(), null))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        long switched = db.queryForObject("INSERT INTO career_assessments(account_id,resume_version,direction_id,source_digest,status,attempt) VALUES (?,1,'data-engineering',?,'WAITING_TEXT',1) RETURNING id", Long.class, owner, "b".repeat(64));
        db.update("UPDATE career_targets SET assessment_id=? WHERE account_id=?", switched, owner);
        var input = new DiagnosisReports.Input("java-backend", "总结", List.of(new DiagnosisReports.Finding("sql", "PASSED", "能解释查询")), null);
        var report = reports.complete(owner, session, "request", input);
        assertThat(report.presentation()).isEqualTo("未评估");
        assertThat(report.pending()).contains("java-backend:api");
        assertThat(reports.complete(owner, session, "request", input)).isEqualTo(report);
        assertThat(capabilities.history(owner, "sql")).hasSize(1);
        assertThatThrownBy(() -> reports.get(other, session)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        db.update("UPDATE career_targets SET assessment_id=? WHERE account_id=?", assessment, owner);
        sqlSession.clearCache();
        assertThat(plans.current(owner).report().matchPercent()).isNull();
        assertThat(reports.hasCompleted(owner)).isTrue();
        assertThat(reports.hasCompleted(other)).isFalse();
    }
    private long account(String name) { return db.queryForObject("INSERT INTO accounts(email,display_name,password_hash,role) VALUES (?,?, 'fixture','STUDENT') RETURNING id", Long.class, name+"@test.invalid", name); }
}
