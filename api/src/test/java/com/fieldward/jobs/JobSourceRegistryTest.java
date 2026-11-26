package com.fieldward.jobs;

import com.fieldward.PostgresIntegrationTest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

class JobSourceRegistryTest extends PostgresIntegrationTest {
    @Autowired JdbcTemplate db;
    @Autowired JobMapper mapper;
    @Test @Transactional void registersAuthorizedSourceAndHidesOnRevocationOrRemoval() {
        class Fake implements JobSource {
            boolean allowed = true;
            public String id() { return "registry"; }
            public boolean authorized() { return allowed; }
            public Set<String> allowedHosts() { return Set.of("example.com"); }
            public SourceBatch fetchChanges(String cursor) { return new SourceBatch(null, List.of(new SourceJob("1","Title","raw","https://example.com/1"))); }
        }
        var fake = new Fake();
        new JobSourceRegistry(mapper, List.of(fake)).reconcile();
        new JobSyncService(mapper, List.of(fake)).sync("registry");
        assertThat(mapper.listActive()).extracting(JobMapper.JobView::sourceId).contains("registry");
        fake.allowed = false;
        new JobSourceRegistry(mapper, List.of(fake)).reconcile();
        assertThat(mapper.listActive()).extracting(JobMapper.JobView::sourceId).doesNotContain("registry");
        fake.allowed = true;
        new JobSourceRegistry(mapper, List.of(fake)).reconcile();
        assertThat(mapper.listActive()).extracting(JobMapper.JobView::sourceId).contains("registry");
        new JobSourceRegistry(mapper, List.of()).reconcile();
        assertThat(mapper.listActive()).extracting(JobMapper.JobView::sourceId).doesNotContain("registry");
    }
}
