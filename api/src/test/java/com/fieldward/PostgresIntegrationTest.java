package com.fieldward;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import org.flywaydb.core.Flyway;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = {"fieldward.admin.email=", "fieldward.admin.password=", "fieldward.mentor.api-key="})
public abstract class PostgresIntegrationTest {
    private static final EmbeddedPostgres DATABASE = start();

    private static EmbeddedPostgres start() {
        try {
            var db = EmbeddedPostgres.start();
            // V1 only installs pgvector. Embedded PostgreSQL exercises the remaining migrations unchanged.
            Flyway.configure().dataSource(db.getPostgresDatabase()).baselineVersion("1").load().baseline();
            return db;
        } catch (IOException error) {
            throw new IllegalStateException("Cannot start isolated test PostgreSQL", error);
        }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> DATABASE.getJdbcUrl("postgres", "postgres"));
        properties.add("spring.datasource.username", () -> "postgres");
        properties.add("spring.datasource.password", () -> "");
    }
}
