package com.fieldward.mentor;

import com.fieldward.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

class ExplorationFlowTest extends PostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired CareerPlans plans;
    @Autowired MentorTurns turns;
    @Autowired WorkspaceController workspaceController;
    @Autowired org.apache.ibatis.session.SqlSession sqlSession;
    @Autowired org.springframework.context.ApplicationContext application;

    @Test @Transactional
    void minimalResumeEnablesIdempotentExplorationAndSharedPrivateMemory() {
        long owner = account("explore-owner"), other = account("explore-other");
        long conversation = submitMinimalResume(owner), otherConversation = submitMinimalResume(other);
        assertThat(plans.beginExploration(owner).conversationId()).isEqualTo(Long.toString(conversation));
        plans.beginExploration(owner);
        assertThat(turns.history(owner, null, null, null, true).turns()).hasSize(1);
        assertThat(turns.history(owner, null, null, null, true).turns().getFirst().mentorReply()).contains("？");
        var main = turns.claim(owner, null, "10000000-0000-0000-0000-000000000001", "我想学习数据库。", true);
        turns.complete(main, "好的", java.util.List.of());
        var principal = org.mockito.Mockito.mock(com.fieldward.account.AccountPrincipal.class);
        org.mockito.Mockito.when(principal.accountId()).thenReturn(owner);
        assertThat(workspaceController.workspace(principal).nextStep()).contains("数据库");
        var side = turns.createSide(owner, "通用学习");
        var sideTurn = turns.claim(owner, Long.valueOf(side.id()), "10000000-0000-0000-0000-000000000002", "数据库怎么入门？", true);
        assertThat(turns.context(sideTurn).messages().toString()).contains("我想学习数据库", "当前目标与进度");
        turns.complete(sideTurn, "从基础开始", java.util.List.of());
        var sideNote = turns.claim(owner, Long.valueOf(side.id()), "10000000-0000-0000-0000-000000000004", "我喜欢做网页。", true);
        turns.complete(sideNote, "了解", java.util.List.of());
        var mainAgain = turns.claim(owner, null, "10000000-0000-0000-0000-000000000005", "网页怎么入门？", true);
        assertThat(turns.context(mainAgain).messages().toString()).contains("我喜欢做网页");
        plans.beginExploration(other);
        jdbc.update("INSERT INTO career_assessments(account_id,resume_version,direction_id,source_digest,status,attempt) VALUES (?,1,'java-backend',?,'WAITING_TEXT',1)", other, "a".repeat(64));
        jdbc.update("INSERT INTO career_targets(account_id,assessment_id) SELECT ?,id FROM career_assessments WHERE account_id=?", other, other);
        var otherPrincipal = org.mockito.Mockito.mock(com.fieldward.account.AccountPrincipal.class);
        org.mockito.Mockito.when(otherPrincipal.accountId()).thenReturn(other);
        assertThat(workspaceController.workspace(otherPrincipal).nextStep()).contains("Java").doesNotContain("数据库");
        var outsider = turns.claim(other, null, "10000000-0000-0000-0000-000000000003", "数据库怎么入门？", true);
        assertThat(turns.context(outsider).messages().toString()).contains("WAITING_TEXT", "Java").doesNotContain("我想学习数据库");
        assertThat(otherConversation).isNotEqualTo(conversation);
        long assessment = jdbc.queryForObject("INSERT INTO career_assessments(account_id,resume_version,direction_id,source_digest,status,attempt,report) VALUES (?,1,'java-backend',?,'READY',1,CAST(? AS jsonb)) RETURNING id", Long.class, owner, "b".repeat(64), "{\"interviewThreshold\":0,\"coreCovered\":false,\"skills\":[],\"roadmap\":[{\"title\":\"Java 基础\"}]}");
        jdbc.update("INSERT INTO career_targets(account_id,assessment_id) VALUES (?,?)", owner, assessment);
        String state = "{\"phase\":\"LEARNING\",\"topics\":[\"Java 基础\",\"SQL\"],\"selfMarks\":{},\"testTopics\":[],\"grades\":[],\"studyTopics\":[\"Java 基础\",\"SQL\"],\"lessonIndex\":1}";
        jdbc.update("INSERT INTO career_skill_sessions(account_id,assessment_id,conversation_id,side_chat_id,state) VALUES (?,?,?,?,CAST(? AS jsonb))", owner, assessment, conversation, Long.valueOf(side.id()), state);
        assertThat(workspaceController.workspace(principal).nextStep()).contains("SQL").doesNotContain("数据库。");
        jdbc.update("UPDATE career_skill_sessions SET state=jsonb_set(state,'{phase}', '\"TEST\"') || CAST(? AS jsonb) WHERE assessment_id=?", "{\"testTopics\":[\"Java 基础\",\"SQL\"],\"grades\":[{\"topic\":\"Java 基础\",\"mark\":\"GREEN\",\"score\":90,\"feedback\":\"ok\"}]}", assessment);
        sqlSession.clearCache(); // A new request has a fresh MyBatis session.
        assertThat(workspaceController.workspace(principal).nextStep()).contains("SQL", "核验");
    }
    private long account(String name) { return jdbc.queryForObject("INSERT INTO accounts(email,display_name,password_hash,role) VALUES (?,?, 'fixture','STUDENT') RETURNING id", Long.class, name+"@test.invalid", name); }
    private long submitMinimalResume(long account) {
        String content = "{\"profile\":{\"name\":\"测试\",\"email\":\"\",\"phone\":\"\"},\"education\":{\"school\":\"测试大学\",\"major\":\"\",\"degree\":\"\",\"graduation\":\"\"},\"experiences\":[],\"skills\":[]}";
        jdbc.update("INSERT INTO resume_drafts(account_id,content,revision) VALUES (?,CAST(? AS jsonb),1)", account, content);
        try {
            Class<?> type = Class.forName("com.fieldward.resume.ResumeSubmissions");
            var method = type.getDeclaredMethod("submitDraft", long.class, long.class);
            method.setAccessible(true);
            method.invoke(application.getBean(type), account, 1L);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        return jdbc.queryForObject("SELECT id FROM mentor_main_conversations WHERE account_id=?", Long.class, account);
    }
}
