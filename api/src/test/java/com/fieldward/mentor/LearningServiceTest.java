package com.fieldward.mentor;

import com.fieldward.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

class LearningServiceTest extends PostgresIntegrationTest {
    @Autowired JdbcTemplate db;
    @Autowired LearningService service;
    @Autowired MentorTurns turns;

    @Test @Transactional
    void freelyOpensAndReusesAccountScopedTopicWithoutChangingEvidence() {
        long owner = account("learning-owner"), other = account("learning-other");
        conversation(owner); conversation(other);
        db.update("INSERT INTO capability_evidence(account_id,capability_id,source_type,source_id,outcome) VALUES (?,'sql','DIAGNOSIS','old','PASSED')", owner);
        db.update("INSERT INTO mentor_side_chats(conversation_id,title) SELECT id,'学习：SQL' FROM mentor_main_conversations WHERE account_id=?", owner);
        var first = service.openTopic(owner, "sql");
        assertThat(service.openTopic(owner, "sql").id()).isEqualTo(first.id());
        var initial = turns.history(owner, Long.valueOf(first.id()), null, null, true).turns();
        assertThat(initial).hasSize(1);
        assertThat(initial.getFirst().mentorReply()).contains("SQL", "？");
        db.update("UPDATE mentor_main_conversations SET career_onboarding_required=false WHERE account_id=?", owner);
        var turn = turns.claim(owner, Long.valueOf(first.id()), "10000000-0000-0000-0000-000000000011", "请教我", true);
        assertThat(turns.context(turn).messages().toString()).contains("SQL", "表结构", "练习");
        assertThat(service.openTopic(other, "sql").id()).isNotEqualTo(first.id());
        assertThat(service.openTopic(owner, "java-backend:api").id()).isNotEqualTo(first.id());
        assertThat(db.queryForObject("SELECT count(*) FROM capability_evidence WHERE account_id=?", Integer.class, owner)).isEqualTo(1);
        assertThatThrownBy(() -> service.openTopic(owner, "does-not-exist")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
    private long account(String name) { return db.queryForObject("INSERT INTO accounts(email,display_name,password_hash,role) VALUES (?,?, 'fixture','STUDENT') RETURNING id", Long.class, name+"@test.invalid", name); }
    private void conversation(long account) {
        db.update("INSERT INTO resume_versions(account_id,version,draft_revision,content) VALUES (?,1,1,'{}')", account);
        db.update("INSERT INTO mentor_main_conversations(account_id,first_resume_version) VALUES (?,1)", account);
    }
}
