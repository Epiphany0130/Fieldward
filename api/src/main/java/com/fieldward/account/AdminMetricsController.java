package com.fieldward.account;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/admin/metrics")
class AdminMetricsController {
    private final JdbcTemplate db;
    AdminMetricsController(JdbcTemplate db) { this.db = db; }
    @GetMapping Map<String, Object> metrics() {
        return Map.of("registrations", count("SELECT count(*) FROM accounts WHERE role='STUDENT'"), "diagnosisSessions", count("SELECT count(*) FROM realtime_sessions WHERE purpose='DIAGNOSIS'"), "learningChats", count("SELECT count(*) FROM learning_topic_chats"), "interviewSessions", count("SELECT count(*) FROM realtime_sessions WHERE purpose='INTERVIEW'"), "applications", count("SELECT count(*) FROM job_applications"));
    }
    private long count(String sql) { return db.queryForObject(sql, Long.class); }
}
