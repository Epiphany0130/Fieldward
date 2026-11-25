package com.fieldward.setup;

import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    private final JdbcTemplate jdbc;

    public HealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/api/health")
    public ResponseEntity<Status> health() {
        try {
            if (Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector')",
                    Boolean.class))) {
                return ResponseEntity.ok(new Status("ready"));
            }
        } catch (DataAccessException ignored) {
            return unavailable();
        }
        return unavailable();
    }

    private ResponseEntity<Status> unavailable() {
        return ResponseEntity.status(503).body(new Status("unavailable"));
    }

    public record Status(String status) {}
}
