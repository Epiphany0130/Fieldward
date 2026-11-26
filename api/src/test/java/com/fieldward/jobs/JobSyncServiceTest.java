package com.fieldward.jobs;

import com.fieldward.PostgresIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

class JobSyncServiceTest extends PostgresIntegrationTest {
    @Autowired JdbcTemplate db;
    @Autowired JobMapper mapper;

    @Test @Transactional
    void incrementalUpdatesAreVersionedAndManualRemovalSurvivesSyncFailure() {
        db.update("INSERT INTO job_sources(id, name, authorized) VALUES ('fake', 'Fake', true)");
        class Fake implements JobSource {
            int calls;
            public String id() { return "fake"; }
            public boolean authorized() { return true; }
            public java.util.Set<String> allowedHosts() { return java.util.Set.of("example.com"); }
            public SourceBatch fetchChanges(String cursor) {
                calls++;
                if (calls == 3) throw new IllegalStateException("unavailable");
                assertThat(cursor).isEqualTo(calls == 1 ? null : "one");
                return new SourceBatch(calls == 1 ? "one" : "two", List.of(
                        new SourceJob("123", "Engineer", calls == 1 ? "SQL" : "SQL and Java", "https://example.com/123")));
            }
        }
        var service = new JobSyncService(mapper, List.of(new Fake()));
        service.sync("fake");
        assertThat(db.queryForObject("SELECT status FROM jobs WHERE source_id='fake'", String.class)).isEqualTo("ACTIVE");
        service.sync("fake");
        assertThat(db.queryForObject("SELECT count(*) FROM jobs WHERE source_id='fake'", Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT count(*) FROM job_versions WHERE job_id IN (SELECT id FROM jobs WHERE source_id='fake')", Integer.class)).isEqualTo(2);
        assertThat(db.queryForObject("SELECT requirements FROM job_versions WHERE job_id IN (SELECT id FROM jobs WHERE source_id='fake') ORDER BY id DESC LIMIT 1", String.class)).isEqualTo("SQL and Java");
        assertThatThrownBy(() -> service.sync("fake")).isInstanceOf(IllegalStateException.class);
        assertThat(db.queryForObject("SELECT status FROM jobs WHERE source_id='fake'", String.class)).isEqualTo("ACTIVE");
        long id = db.queryForObject("SELECT id FROM jobs WHERE source_id='fake'", Long.class);
        service.remove(id);
        assertThat(db.queryForObject("SELECT status FROM jobs WHERE id=?", String.class, id)).isEqualTo("REMOVED");
    }
    @Test @Transactional
    void explicitClosureAndReopeningRespectManualRemoval() {
        db.update("INSERT INTO job_sources(id,name,authorized) VALUES ('closure','Closure',true)");
        class Fake implements JobSource {
            boolean active = true;
            public String id() { return "closure"; }
            public boolean authorized() { return true; }
            public java.util.Set<String> allowedHosts() { return java.util.Set.of("example.com"); }
            public SourceBatch fetchChanges(String cursor) { return new SourceBatch(null, List.of(new SourceJob("1", "Title", "Original text", "https://example.com/1", active))); }
        }
        var fake = new Fake(); var service = new JobSyncService(mapper, List.of(fake));
        service.sync("closure"); fake.active = false; service.sync("closure");
        long id = db.queryForObject("SELECT id FROM jobs WHERE source_id='closure'", Long.class);
        assertThat(db.queryForObject("SELECT status FROM jobs WHERE id=?", String.class, id)).isEqualTo("SOURCE_CLOSED");
        fake.active = true; service.sync("closure");
        assertThat(db.queryForObject("SELECT status FROM jobs WHERE id=?", String.class, id)).isEqualTo("ACTIVE");
        service.remove(id); service.sync("closure");
        assertThat(db.queryForObject("SELECT status FROM jobs WHERE id=?", String.class, id)).isEqualTo("REMOVED");
    }
    @Test @Transactional
    void refusesUnauthorizedSourceBeforeFetching() {
        db.update("INSERT INTO job_sources(id,name,authorized) VALUES ('unauthorized','Unauthorized',false)");
        JobSource source = new JobSource() {
            public String id() { return "unauthorized"; }
            public boolean authorized() { return false; }
            public java.util.Set<String> allowedHosts() { return java.util.Set.of("example.com"); }
            public SourceBatch fetchChanges(String cursor) { throw new AssertionError("must not fetch"); }
        };
        assertThatThrownBy(() -> new JobSyncService(mapper, List.of(source)).sync("unauthorized")).isInstanceOf(IllegalStateException.class);
    }
}
