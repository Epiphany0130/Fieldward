package com.fieldward.mentor;

import com.fieldward.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

class DirectionChangeServiceTest extends PostgresIntegrationTest {
    @Autowired JdbcTemplate db;
    @Autowired DirectionChangeService service;
    @Autowired CareerPlans plans;
    @Test @Transactional
    void requiresConfirmationAndKeepsSharedEvidencePrivate() {
        long owner = account("direction-owner"), other = account("direction-other");
        db.update("INSERT INTO resume_versions(account_id,version,draft_revision,content) VALUES (?,1,1,'{}')", owner);
        long assessment = db.queryForObject("INSERT INTO career_assessments(account_id,resume_version,direction_id,source_digest,status,attempt) VALUES (?,1,'java-backend',?,'WAITING_TEXT',1) RETURNING id", Long.class, owner, "a".repeat(64));
        db.update("INSERT INTO career_targets(account_id,assessment_id) VALUES (?,?)", owner, assessment);
        db.update("INSERT INTO capability_evidence(account_id,capability_id,source_type,source_id,outcome) VALUES (?,'sql','DIAGNOSIS','old','PASSED')", owner);
        var proposal = service.propose(owner, "data-engineering");
        assertThat(proposal.shared()).contains("SQL");
        db.update("INSERT INTO resume_versions(account_id,version,draft_revision,content) VALUES (?,2,2,'{}')", owner);
        assertThat(proposal.introduction()).isNotBlank();
        assertThat(proposal.advice()).isNotBlank();
        assertThat(current(owner)).isEqualTo("java-backend");
        assertThatThrownBy(() -> service.confirmDirectionChange(other, proposal.id())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(service.confirmDirectionChange(owner, proposal.id()).directionId()).isEqualTo("data-engineering");
        assertThat(service.confirmDirectionChange(owner, proposal.id()).directionId()).isEqualTo("data-engineering");
        assertThat(current(owner)).isEqualTo("data-engineering");
        assertThat(db.queryForObject("SELECT a.resume_version FROM career_targets t JOIN career_assessments a ON a.id=t.assessment_id WHERE t.account_id=?", Long.class, owner)).isEqualTo  (2L);
        long replacement = db.queryForObject("INSERT INTO career_assessments(account_id,resume_version,direction_id,source_digest,status,attempt) VALUES (?,2,'data-engineering',?,'WAITING_TEXT',1) RETURNING id", Long.class, owner, "b".repeat(64));
        db.update("UPDATE career_targets SET assessment_id=? WHERE account_id=?", replacement, owner);
        assertThat(service.confirmDirectionChange(owner, proposal.id()).directionId()).isEqualTo("data-engineering");
        var next = service.propose(owner, "web-frontend");
        service.confirmDirectionChange(owner, next.id());
        assertThatThrownBy(() -> service.confirmDirectionChange(owner, proposal.id())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(db.queryForObject("SELECT count(*) FROM capability_evidence WHERE account_id=? AND capability_id='sql'", Integer.class, owner)).isEqualTo(1);
    }
    @Test @Transactional
    void usesCurrentDirectionDetailsForAssessmentsAndProposals() {
        long owner = account("direction-details");
        db.update("INSERT INTO resume_versions(account_id,version,draft_revision,content) VALUES (?,1,1,'{}')", owner);
        long assessment = db.queryForObject("INSERT INTO career_assessments(account_id,resume_version,direction_id,source_digest,status,attempt) VALUES (?,1,'java-backend',?,'WAITING_TEXT',1) RETURNING id", Long.class, owner, "c".repeat(64));
        db.update("INSERT INTO career_targets(account_id,assessment_id) VALUES (?,?)", owner, assessment);
        db.update("UPDATE career_directions SET title='Updated Java' WHERE id='java-backend'");
        db.update("UPDATE career_directions SET description='Updated data description' WHERE id='data-engineering'");
        assertThat(plans.archived(owner, assessment).directionTitle()).isEqualTo("Updated Java");
        assertThat(service.propose(owner, "data-engineering").introduction()).isEqualTo("Updated data description");
    }
    private String current(long account) { return db.queryForObject("SELECT a.direction_id FROM career_targets t JOIN career_assessments a ON a.id=t.assessment_id WHERE t.account_id=?", String.class, account); }
    private long account(String name) { return db.queryForObject("INSERT INTO accounts(email,display_name,password_hash,role) VALUES (?,?, 'fixture','STUDENT') RETURNING id", Long.class, name+"@test.invalid", name); }
}
