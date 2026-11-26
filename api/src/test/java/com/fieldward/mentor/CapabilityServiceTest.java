package com.fieldward.mentor;

import com.fieldward.PostgresIntegrationTest;
import org.apache.ibatis.session.SqlSession;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CapabilityServiceTest extends PostgresIntegrationTest {
    @Autowired CapabilityService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired SqlSession session;

    @Test
    @Transactional
    void sharedEvidenceIsPrivateAndSurvivesRouteReplacement() {
        long owner = account("owner");
        long other = account("other");
        var input = new CapabilityService.EvidenceInput("DIAGNOSIS", "session-1", "PASSED");
        var evidence = service.recordEvidence(owner, "sql", input);
        assertThat(evidence.createdAt()).isNotNull();
        assertThat(service.recordEvidence(owner, "sql", input)).isEqualTo(evidence);
        for (String direction : new String[]{"java-backend", "data-engineering"}) {
            var route = service.currentRoute(owner, direction);
            assertThat(route.capabilities()).filteredOn(item -> item.capabilityId().equals("sql"))
                    .singleElement().satisfies(item -> assertThat(item.evidence()).containsExactly(evidence));
            assertThat(service.currentRoute(other, direction).capabilities())
                    .allSatisfy(item -> assertThat(item.evidence()).isEmpty());
        }
        jdbc.update("INSERT INTO career_route_versions (direction_id, version) VALUES ('java-backend',2)");
        // The fixture writes through JDBC outside MyBatis within this rollback-only transaction.
        session.clearCache();
        var updated = service.currentRoute(owner, "java-backend");
        assertThat(updated.version()).isEqualTo(2);
        assertThat(updated.capabilities()).isEmpty();
        assertThat(service.history(owner, "sql")).containsExactly(evidence);
        assertThat(service.history(other, "sql")).isEmpty();
    }

    @Test
    @Transactional
    void evidenceSourceCannotBeRewrittenOrSelfVerified() {
        long owner = account("retry");
        var input = new CapabilityService.EvidenceInput("LEARNING", "exercise-1", "PASSED");
        var original = service.recordEvidence(owner, "sql", input);
        assertThatThrownBy(() -> service.recordEvidence(owner, "sql",
                new CapabilityService.EvidenceInput("LEARNING", "exercise-1", "NEEDS_PRACTICE")))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.recordEvidence(owner, "sql",
                new CapabilityService.EvidenceInput("SELF_REPORT", "claim", "PASSED")))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(service.history(owner, "sql")).containsExactly(original);
    }

    @Test
    void repeatedMigrationPreservesSeedAndSeparatesUnrelatedModules() {
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        for (var direction : CareerCatalog.DIRECTIONS) {
            assertThat(service.currentRoute(1, direction.id()).capabilities())
                    .extracting(CapabilityService.RouteCapability::title)
                    .containsExactlyElementsOf(direction.skills().stream().map(CareerCatalog.Skill::title).toList());
        }
        assertThat(service.currentRoute(1, "software-testing").capabilities())
                .extracting(CapabilityService.RouteCapability::capabilityId).doesNotContainAnyElementsOf(
                        service.currentRoute(1, "harmony-app").capabilities().stream()
                                .map(CapabilityService.RouteCapability::capabilityId).toList());
    }

    private long account(String name) {
        return jdbc.queryForObject("""
                INSERT INTO accounts (email, display_name, password_hash, role)
                VALUES (?, ?, 'fixture', 'STUDENT') RETURNING id
                """, Long.class, name + "@test.invalid", name);
    }
}
