package com.fieldward.mentor;

import com.fieldward.PostgresIntegrationTest;
import com.fieldward.jobs.JobSource;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@org.springframework.context.annotation.Import(ApplicationConversationTest.Config.class)
class ApplicationConversationTest extends PostgresIntegrationTest {
    @org.springframework.boot.test.context.TestConfiguration static class Config {
        @org.springframework.context.annotation.Bean JobSource applicationConversationSource() { return new JobSource() {
            public String id() { return "application-chat"; }
            public boolean authorized() { return true; }
            public Set<String> allowedHosts() { return Set.of("example.com"); }
            public SourceBatch fetchChanges(String cursor) { throw new UnsupportedOperationException(); }
        }; }
    }
    @Autowired JdbcTemplate db;
    @Autowired MentorTurns turns;
    @Autowired org.apache.ibatis.session.SqlSession session;
    @Test @Transactional void sideMessageUpdatesOnceNotifiesMainAndIsIsolated() {
        long a=account("app-chat-a"), b=account("app-chat-b");
        long conversation=conversation(a); conversation(b);
        long job=db.queryForObject("INSERT INTO jobs(source_id,source_job_id) VALUES ('application-chat','a') RETURNING id",Long.class);
        db.update("INSERT INTO job_versions(job_id,title,requirements,source_url) VALUES (?,'Java 实习生','SQL','https://example.com/a')",job);
        db.update("UPDATE credit_accounts SET balance=0 WHERE account_id=?",a);
        var side=turns.createSide(a,"投递");
        String request=UUID.randomUUID().toString();
        var turn=turns.claim(a,Long.valueOf(side.id()),request,"我已投递Java 实习生",false);
        assertThat(turns.completeApplicationMessage(turn).mentorReply()).contains("Java 实习生", "已投递");
        session.clearCache();
        assertThat(turns.claim(a,Long.valueOf(side.id()),request,"我已投递Java 实习生",true).status()).isEqualTo(MentorTurnMapper.Status.COMPLETED);
        assertThat(db.queryForObject("SELECT count(*) FROM credit_reservations WHERE account_id=?",Integer.class,a)).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM application_requests WHERE account_id=?",Integer.class,a)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT count(*) FROM job_applications WHERE account_id=?",Integer.class,b)).isZero();
        assertThat(turns.history(a,null,null,null,true).turns()).singleElement().satisfies(v -> assertThat(v.mentorReply()).contains("Java 实习生"));
        var ambiguous=turns.claim(a,null,UUID.randomUUID().toString(),"我已投递",false);
        assertThat(turns.completeApplicationMessage(ambiguous).mentorReply()).contains("明确");
        assertThat(db.queryForObject("SELECT count(*) FROM application_requests WHERE account_id=?",Integer.class,a)).isEqualTo(1);
    }
    private long account(String name) { return db.queryForObject("INSERT INTO accounts(email,display_name,password_hash,role) VALUES (?,?, 'x','STUDENT') RETURNING id",Long.class,name+"@test.invalid",name); }
    private long conversation(long id) { db.update("INSERT INTO resume_versions(account_id,version,draft_revision,content) VALUES (?,1,1,'{}')",id); return db.queryForObject("INSERT INTO mentor_main_conversations(account_id,first_resume_version,career_onboarding_required) VALUES (?,1,false) RETURNING id",Long.class,id); }
}
