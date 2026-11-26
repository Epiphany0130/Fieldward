package com.fieldward.mentor;

import com.fieldward.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import jakarta.servlet.Filter;
import org.springframework.mock.web.MockHttpSession;
import tools.jackson.databind.json.JsonMapper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LearningSecurityTest extends PostgresIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired Filter springSecurityFilterChain;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder passwords;
    @Autowired JsonMapper json;

    @Test void adminCannotOpenStudentTopic() throws Exception {
        db.update("INSERT INTO accounts(email,display_name,password_hash,role) VALUES ('learning-admin@test.invalid','admin',?,'ADMIN')", passwords.encode("test-password"));
        var mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
        var session = new MockHttpSession();
        var csrf = json.readTree(mvc.perform(get("/api/auth/csrf").session(session)).andReturn().getResponse().getContentAsString());
        var login = mvc.perform(post("/api/auth/login").session(session).header(csrf.get("headerName").asString(), csrf.get("token").asString())
                .contentType("application/json").content("{\"email\":\"learning-admin@test.invalid\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk()).andReturn();
        session = (MockHttpSession) login.getRequest().getSession(false);
        csrf = json.readTree(mvc.perform(get("/api/auth/csrf").session(session)).andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/learning/topics/sql").session(session).header(csrf.get("headerName").asString(), csrf.get("token").asString())
                .contentType("application/json").content("{\"accountId\":\"1\"}"))
                .andExpect(status().isForbidden());
    }
}
