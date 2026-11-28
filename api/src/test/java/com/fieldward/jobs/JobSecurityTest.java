package com.fieldward.jobs;

import com.fieldward.PostgresIntegrationTest;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.mock.web.MockHttpSession;
import tools.jackson.databind.json.JsonMapper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@org.springframework.context.annotation.Import(JobSecurityTest.Config.class)
class JobSecurityTest extends PostgresIntegrationTest {
    @org.springframework.boot.test.context.TestConfiguration static class Config {
        @org.springframework.context.annotation.Bean JobSource securitySource() { return new JobSource() {
            public String id() { return "security"; }
            public boolean authorized() { return true; }
            public java.util.Set<String> allowedHosts() { return java.util.Set.of("example.com"); }
            public SourceBatch fetchChanges(String cursor) { throw new UnsupportedOperationException(); }
        }; }
    }
    @Autowired WebApplicationContext context;
    @Autowired Filter springSecurityFilterChain;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder passwords;
    @Autowired JsonMapper json;

    @Test @org.springframework.transaction.annotation.Transactional void rolesAndActiveJobVisibilityAreEnforced() throws Exception {
        db.update("INSERT INTO accounts(email,display_name,password_hash,role) VALUES ('job-student@test.invalid','student',?,'STUDENT'),('job-admin@test.invalid','admin',?,'ADMIN')", passwords.encode("test-password"), passwords.encode("test-password"));
        db.update("INSERT INTO jobs(source_id,source_job_id,status) VALUES ('security','active','ACTIVE'),('security','removed','REMOVED')");
        db.update("INSERT INTO job_versions(job_id,title,requirements,source_url) SELECT id,source_job_id,'requirements','https://example.com/' || source_job_id FROM jobs WHERE source_id='security'");
        var mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
        mvc.perform(get("/api/admin/jobs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/applications")).andExpect(status().isUnauthorized());
        var student = login(mvc, "job-student@test.invalid");
        var admin = login(mvc, "job-admin@test.invalid");
        mvc.perform(get("/api/admin/jobs").session(student)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/jobs").session(admin)).andExpect(status().isOk());
        mvc.perform(get("/api/applications").session(admin)).andExpect(status().isForbidden());
        mvc.perform(get("/api/applications").session(student)).andExpect(status().isOk());
        mvc.perform(get("/api/jobs").session(student)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("active"))).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("removed"))));
    }
    private MockHttpSession login(org.springframework.test.web.servlet.MockMvc mvc, String email) throws Exception {
        var session = new MockHttpSession();
        var csrf = json.readTree(mvc.perform(get("/api/auth/csrf").session(session)).andReturn().getResponse().getContentAsString());
        return (MockHttpSession) mvc.perform(post("/api/auth/login").session(session).header(csrf.get("headerName").asString(), csrf.get("token").asString())
                .contentType("application/json").content("{\"email\":\"" + email + "\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
}
