package com.fieldward.jobs;

import com.fieldward.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

class ApplicationStoreTest extends PostgresIntegrationTest {
    @Autowired JdbcTemplate db;
    @Autowired ApplicationStore store;
    @Test @Transactional void retriesDoNotOverwriteNewerProgressAndAccountsAreIsolated() {
        long a = db.queryForObject("INSERT INTO accounts(email,display_name,password_hash,role) VALUES ('application-a@test','a','x','STUDENT') RETURNING id",Long.class);
        long b = db.queryForObject("INSERT INTO accounts(email,display_name,password_hash,role) VALUES ('application-b@test','b','x','STUDENT') RETURNING id",Long.class);
        db.update("INSERT INTO job_sources(id,name,authorized) VALUES ('application-test','test',true)");
        long job = db.queryForObject("INSERT INTO jobs(source_id,source_job_id) VALUES ('application-test','a') RETURNING id",Long.class);
        store.favorite(a,job,true);
        store.updateProgress(a,job,"APPLIED","one");
        store.updateProgress(a,job,"INTERVIEW","two");
        store.updateProgress(a,job,"APPLIED","one");
        assertThat(store.list(a)).singleElement().satisfies(v -> { assertThat(v.status()).isEqualTo("INTERVIEW"); assertThat(v.favorite()).isTrue(); });
        assertThat(store.list(b)).isEmpty();
        store.updateProgress(b,job,"APPLIED","one");
        assertThat(store.list(b)).singleElement().satisfies(v -> assertThat(v.status()).isEqualTo("APPLIED"));
        assertThatThrownBy(() -> store.updateProgress(a,job,"REJECTED","one")).isInstanceOf(RuntimeException.class);
    }
}
