package com.fieldward.jobs;

import com.fieldward.PostgresIntegrationTest;
import jakarta.servlet.Filter;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.mock.web.MockHttpSession;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(JobPolicySecurityTest.Config.class)
class JobPolicySecurityTest extends PostgresIntegrationTest {
    @Autowired JdbcTemplate db;
    @Autowired JobSyncService service;
    @Autowired JobSourceRegistry registry;
    @Autowired Fake source;
    @Autowired WebApplicationContext context;
    @Autowired Filter springSecurityFilterChain;
    @Autowired PasswordEncoder passwords;
    @Autowired JsonMapper json;
    @TestConfiguration static class Config { @Bean Fake policySource() { return new Fake(); } }
    static class Fake implements JobSource {
        volatile boolean allowed = true;
        volatile Set<String> hosts = Set.of("example.com");
        public String id() { return "policy"; }
        public boolean authorized() { return allowed; }
        public Set<String> allowedHosts() { return hosts; }
        public SourceBatch fetchChanges(String cursor) { return new SourceBatch("saved-cursor", List.of(new SourceJob("1", "Policy Job", "original", "https://example.com/1"))); }
    }
    @Test void studentReadUsesCurrentAuthorizationAndHostsAndRegistrationKeepsCursor() throws Exception {
        db.update("INSERT INTO accounts(email,display_name,password_hash,role) VALUES ('job-policy@test.invalid','student',?,'STUDENT')", passwords.encode("test-password"));
        try {
            service.sync("policy");
            var mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
            var session = new MockHttpSession();
            var csrf = json.readTree(mvc.perform(get("/api/auth/csrf").session(session)).andReturn().getResponse().getContentAsString());
            session = (MockHttpSession) mvc.perform(post("/api/auth/login").session(session).header(csrf.get("headerName").asString(), csrf.get("token").asString()).contentType("application/json").content("{\"email\":\"job-policy@test.invalid\",\"password\":\"test-password\"}")).andExpect(status().isOk()).andReturn().getRequest().getSession(false);
            mvc.perform(get("/api/jobs").session(session)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Policy Job")));
            source.allowed = false;
            mvc.perform(get("/api/jobs").session(session)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Policy Job"))));
            registry.reconcile();
            source.allowed = true; registry.reconcile();
            assertThat(db.queryForObject("SELECT cursor FROM job_sources WHERE id='policy'", String.class)).isEqualTo("saved-cursor");
            source.hosts = Set.of("other.example.com");
            mvc.perform(get("/api/jobs").session(session)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Policy Job"))));
        } finally {
            source.allowed = true; source.hosts = Set.of("example.com");
            db.update("DELETE FROM job_versions WHERE job_id IN (SELECT id FROM jobs WHERE source_id='policy')");
            db.update("DELETE FROM jobs WHERE source_id='policy'"); db.update("DELETE FROM job_sources WHERE id='policy'");
            db.update("DELETE FROM accounts WHERE email='job-policy@test.invalid'");
        }
    }
}
