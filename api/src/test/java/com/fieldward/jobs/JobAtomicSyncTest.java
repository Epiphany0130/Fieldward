package com.fieldward.jobs;

import com.fieldward.PostgresIntegrationTest;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

@Import(JobAtomicSyncTest.Config.class)
class JobAtomicSyncTest extends PostgresIntegrationTest {
    @Autowired JdbcTemplate db;
    @Autowired JobSyncService service;
    @Autowired Fake source;
    @TestConfiguration static class Config { @Bean Fake source() { return new Fake(); } }
    static class Fake implements JobSource {
        volatile boolean invalid;
        public String id() { return "atomic"; }
        public Set<String> allowedHosts() { return Set.of("example.com"); }
        public boolean authorized() { return true; }
        public SourceBatch fetchChanges(String cursor) {
            int next = cursor == null ? 1 : Integer.parseInt(cursor) + 1;
            return new SourceBatch(String.valueOf(next), invalid
                    ? List.of(new SourceJob("good","Good","full original","https://example.com/good"), new SourceJob("bad","Bad","raw","https://evil.invalid/bad"))
                    : List.of(new SourceJob("good","Good","full original","https://example.com/good")));
        }
    }
    @Test void springTransactionRollsBackInvalidBatchAndSerializesConcurrentSyncs() throws Exception {
        assertThat(db.queryForObject("SELECT authorized FROM job_sources WHERE id='atomic'", Boolean.class)).isTrue();
        try {
            source.invalid = true;
            assertThatThrownBy(() -> service.sync("atomic")).isInstanceOf(IllegalStateException.class);
            assertThat(db.queryForObject("SELECT count(*) FROM jobs WHERE source_id='atomic'", Integer.class)).isZero();
            assertThat(db.queryForObject("SELECT cursor FROM job_sources WHERE id='atomic'", String.class)).isNull();
            source.invalid = false;
            var pool = Executors.newFixedThreadPool(2);
            try {
                var a = pool.submit(() -> service.sync("atomic")); var b = pool.submit(() -> service.sync("atomic"));
                a.get(); b.get();
            } finally { pool.shutdownNow(); }
            assertThat(db.queryForObject("SELECT cursor FROM job_sources WHERE id='atomic'", String.class)).isEqualTo("2");
            assertThat(db.queryForObject("SELECT count(*) FROM jobs WHERE source_id='atomic'", Integer.class)).isEqualTo(1);
        } finally {
            source.invalid = false;
            db.update("DELETE FROM job_versions WHERE job_id IN (SELECT id FROM jobs WHERE source_id='atomic')");
            db.update("DELETE FROM jobs WHERE source_id='atomic'"); db.update("DELETE FROM job_sources WHERE id='atomic'");
        }
    }
}
