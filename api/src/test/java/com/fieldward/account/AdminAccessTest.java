package com.fieldward.account;

import com.fieldward.PostgresIntegrationTest;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminAccessTest extends PostgresIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired Filter springSecurityFilterChain;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder passwords;
    @Autowired JsonMapper json;

    @Test @org.springframework.transaction.annotation.Transactional void accessAndCreditOwnership() throws Exception {
        db.update("INSERT INTO accounts(email,display_name,password_hash,role) VALUES ('access-student@test.invalid','student',?,'STUDENT'),('access-other@test.invalid','other',?,'STUDENT'),('access-admin@test.invalid','admin',?,'ADMIN')", passwords.encode("test-password"), passwords.encode("test-password"), passwords.encode("test-password"));
        long studentId = db.queryForObject("SELECT id FROM accounts WHERE email='access-student@test.invalid'", Long.class);
        long adminId = db.queryForObject("SELECT id FROM accounts WHERE email='access-admin@test.invalid'", Long.class);
        var mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
        var student = login(mvc, "access-student@test.invalid");
        var other = login(mvc, "access-other@test.invalid");
        var admin = login(mvc, "access-admin@test.invalid");
        var csrf = json.readTree(mvc.perform(get("/api/auth/csrf").session(student)).andReturn().getResponse().getContentAsString());
        String header = csrf.get("headerName").asString(), token = csrf.get("token").asString();
        mvc.perform(post("/api/admin/users/" + studentId + "/credits").session(student).header(header, token).contentType("application/json").content("{\"points\":5,\"reason\":\"test\",\"requestId\":\"access-test\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/jobs/1/remove").session(student).header(header, token)).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/routes/java-backend").session(student).header(header, token).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/metrics").session(student)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/users/" + studentId + "/role").session(student).header(header, token).contentType("application/json").content("{\"role\":\"ADMIN\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/users/" + studentId + "/password").session(student).header(header, token).contentType("application/json").content("{\"password\":\"new-password\"}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/users/" + studentId).session(student).header(header, token)).andExpect(status().isForbidden());
        csrf = json.readTree(mvc.perform(get("/api/auth/csrf").session(admin)).andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/admin/users/" + studentId + "/credits").session(admin).header(csrf.get("headerName").asString(), csrf.get("token").asString()).contentType("application/json").content("{\"points\":5,\"reason\":\"test reason\",\"requestId\":\"access-test\"}")).andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertEquals(adminId, db.queryForObject("SELECT operator_id FROM credit_ledger WHERE request_id='access-test'", Long.class));
        org.junit.jupiter.api.Assertions.assertEquals("test reason", db.queryForObject("SELECT reason FROM credit_ledger WHERE request_id='access-test'", String.class));
        mvc.perform(get("/api/credits").session(student)).andExpect(content().string(org.hamcrest.Matchers.containsString("access-test")));
        mvc.perform(get("/api/credits").session(other)).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("access-test"))));
        mvc.perform(get("/api/admin/metrics").session(admin)).andExpect(status().isOk()).andExpect(jsonPath("$.registrations").isNumber());
        mvc.perform(get("/api/admin/routes").session(admin)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/routes/capabilities").session(admin)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("java-backend:java")));
        mvc.perform(post("/api/admin/routes/capabilities").session(admin).header(csrf.get("headerName").asString(), csrf.get("token").asString()).contentType("application/json").content("{\"id\":\"new-capability\",\"title\":\"新能力\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/admin/routes/capabilities").session(admin)).andExpect(content().string(org.hamcrest.Matchers.containsString("new-capability")));
        mvc.perform(put("/api/admin/routes/java-backend").session(admin).header(csrf.get("headerName").asString(), csrf.get("token").asString()).contentType("application/json").content("{\"version\":1,\"title\":\"Java updated\",\"description\":\"description\",\"roles\":\"roles\",\"items\":[{\"capabilityId\":\"java-backend:java\",\"title\":\"Java\",\"objective\":\"objective\"}]} ")).andExpect(status().isOk());
        mvc.perform(get("/api/career").session(student)).andExpect(content().string(org.hamcrest.Matchers.containsString("Java updated")));
        mvc.perform(put("/api/admin/routes/java-backend").session(admin).header(csrf.get("headerName").asString(), csrf.get("token").asString()).contentType("application/json").content("{\"version\":1,\"title\":\"stale\",\"description\":\"description\",\"roles\":\"roles\",\"items\":[{\"capabilityId\":\"java-backend:java\",\"title\":\"Java\",\"objective\":\"objective\"}]} ")).andExpect(status().isConflict());
        org.junit.jupiter.api.Assertions.assertEquals("java", db.queryForObject("SELECT legacy_skill_id FROM career_route_items WHERE direction_id='java-backend' AND version=2", String.class));
        org.junit.jupiter.api.Assertions.assertEquals(5, db.queryForObject("SELECT count(*) FROM career_route_items WHERE direction_id='java-backend' AND version=1", Integer.class));
        mvc.perform(post("/api/admin/users/" + studentId + "/password").session(admin).header(csrf.get("headerName").asString(), csrf.get("token").asString()).contentType("application/json").content("{\"password\":\"new-password\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/credits").session(student)).andExpect(status().isUnauthorized());
    }
    @Test @org.springframework.transaction.annotation.Transactional void rolesDeleteAndLastAdministrator() throws Exception {
        db.update("INSERT INTO accounts(email,display_name,password_hash,role) VALUES ('guard-student@test.invalid','student',?,'STUDENT'),('guard-admin@test.invalid','admin',?,'ADMIN')", passwords.encode("test-password"), passwords.encode("test-password"));
        long studentId = db.queryForObject("SELECT id FROM accounts WHERE email='guard-student@test.invalid'", Long.class);
        long adminId = db.queryForObject("SELECT id FROM accounts WHERE email='guard-admin@test.invalid'", Long.class);
        var mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
        var student = login(mvc, "guard-student@test.invalid");
        var admin = login(mvc, "guard-admin@test.invalid");
        var csrf = json.readTree(mvc.perform(get("/api/auth/csrf").session(admin)).andReturn().getResponse().getContentAsString());
        var header = csrf.get("headerName").asString(); var token = csrf.get("token").asString();
        mvc.perform(delete("/api/admin/users/" + adminId).session(admin).header(header, token)).andExpect(status().isConflict());
        mvc.perform(patch("/api/admin/users/" + adminId + "/role").session(admin).header(header, token).contentType("application/json").content("{\"role\":\"STUDENT\"}")).andExpect(status().isConflict());
        mvc.perform(patch("/api/admin/users/" + studentId + "/role").session(admin).header(header, token).contentType("application/json").content("{\"role\":\"ADMIN\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/credits").session(student)).andExpect(status().isUnauthorized());
        var promoted = login(mvc, "guard-student@test.invalid");
        mvc.perform(get("/api/admin/users").session(promoted)).andExpect(status().isOk());
        mvc.perform(delete("/api/admin/users/" + studentId).session(admin).header(header, token)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/users").session(promoted)).andExpect(status().isUnauthorized());
        org.junit.jupiter.api.Assertions.assertEquals(0, db.queryForObject("SELECT count(*) FROM accounts WHERE id=?", Integer.class, studentId));
    }
    private MockHttpSession login(MockMvc mvc, String email) throws Exception {
        var session = new MockHttpSession();
        var csrf = json.readTree(mvc.perform(get("/api/auth/csrf").session(session)).andReturn().getResponse().getContentAsString());
        return (MockHttpSession) mvc.perform(post("/api/auth/login").session(session).header(csrf.get("headerName").asString(), csrf.get("token").asString()).contentType("application/json").content("{\"email\":\"" + email + "\",\"password\":\"test-password\"}")).andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
}
