package com.fieldward.mentor;

import com.fieldward.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

class DynamicCareerDirectionTest extends PostgresIntegrationTest {
    @Autowired JdbcTemplate db;
    @Autowired CareerDirections directions;
    @Autowired CareerFit fit;
    @Autowired LegacyCareerMapper migration;
    @Autowired SkillSessions sessions;
    @Autowired MentorMemory memory;
    @Autowired CareerWrites writes;
    @Autowired org.springframework.transaction.support.TransactionTemplate transactions;
    @Autowired tools.jackson.databind.json.JsonMapper json;

    @Test @org.springframework.transaction.annotation.Transactional void createdDirectionUsesItsCurrentModulesWithoutInventingEvidence() throws Exception {
        db.update("INSERT INTO career_directions(id,title,description,roles) VALUES ('test-design','Design','Description','Designer')");
        db.update("INSERT INTO career_capabilities(id,title) VALUES ('test:design','Design'),('test:second','Design')");
        db.update("INSERT INTO career_route_versions(direction_id,version) VALUES ('test-design',1)");
        db.update("INSERT INTO career_route_items(direction_id,version,position,capability_id,title,objective) VALUES ('test-design',1,1,'test:design','Research','Understand users')");
        db.update("INSERT INTO career_route_items(direction_id,version,position,capability_id,title,objective) VALUES ('test-design',1,2,'test:second','Research','Observe users')");
        var selected = directions.require("test-design");
        var report = fit.analyze(selected.direction(), new CareerFit.Input("", null, false), selected.route());
        assertEquals("test:design", report.skills().getFirst().skillId());
        assertEquals(CareerFit.Level.UNKNOWN, report.skills().getFirst().level());
        assertNull(report.matchPercent());
        assertEquals(java.util.List.of("test:design", "test:second"), report.route().topics());
        assertEquals("Research", report.route().label("test:design"));
        assertEquals("Research", report.route().label("test:second"));
        db.update("INSERT INTO career_route_versions(direction_id,version) VALUES ('test-design',2)");
        db.update("INSERT INTO career_route_items(direction_id,version,position,capability_id,title,objective) VALUES ('test-design',2,1,'test:design','Updated research','Interview users')");
        var updated = directions.require("test-design");
        assertEquals(2, updated.version());
        assertEquals("Interview users", updated.direction().skills().getFirst().objective());
        assertEquals(java.util.List.of("test:design"), updated.route().topics());
        assertEquals("Updated research", updated.route().label("test:design"));

    }
    @Test @org.springframework.transaction.annotation.Transactional void legacyAndNewIdsCannotCollide() throws Exception {
        db.update("INSERT INTO career_capabilities(id,title) VALUES ('java','New Java') ON CONFLICT DO NOTHING");
        db.update("INSERT INTO career_route_versions(direction_id,version) VALUES ('java-backend',2)");
        db.update("INSERT INTO career_route_items(direction_id,version,position,capability_id,legacy_skill_id,title,objective) VALUES ('java-backend',2,1,'java-backend:java','java','Java','Java objective')");
        db.update("INSERT INTO career_route_items(direction_id,version,position,capability_id,title,objective) VALUES ('java-backend',2,2,'java','Java','New objective')");
        var selection = directions.require("java-backend");
        assertEquals(java.util.List.of("java-backend:java", "java"), selection.direction().skills().stream().map(CareerCatalog.Skill::id).toList());
        assertEquals(java.util.List.of("java-backend:java", "java"), selection.route().topics());
        long owner = db.queryForObject("INSERT INTO accounts(email,display_name,password_hash,role) VALUES ('dynamic-collision@test.invalid','Test','hash','STUDENT') RETURNING id", Long.class);
        db.update("INSERT INTO resume_versions(account_id,version,draft_revision,content) VALUES (?,1,1,'{}')", owner);
        var report = fit.analyze(selection.direction(), new CareerFit.Input("", null, false), selection.route());
        long assessment = db.queryForObject("INSERT INTO career_assessments(account_id,resume_version,direction_id,source_digest,status,attempt,report) VALUES (?,1,'java-backend',?,'READY',1,CAST(? AS jsonb)) RETURNING id", Long.class, owner, "b".repeat(64), json.writeValueAsString(report));
        db.update("INSERT INTO mentor_main_conversations(account_id,first_resume_version) VALUES (?,1)", owner);
        var session = sessions.start(owner, assessment, java.util.Map.of("java-backend:java", CareerFit.Mark.RED, "java", CareerFit.Mark.RED));
        assertEquals(2, session.marks().size());
        assertEquals("Java", session.labels().get("java"));
        assertEquals("Java", session.labels().get("java-backend:java"));
        db.update("INSERT INTO career_targets(account_id,assessment_id) VALUES (?,?)", owner, assessment);
        var conversation = db.queryForObject("SELECT id FROM mentor_main_conversations WHERE account_id=?", Long.class, owner);
        var learning = sessions.step(owner, assessment, session.revision(), "");
        assertEquals(SkillSessions.Phase.LEARNING, learning.phase());
        assertTrue(memory.nextStep(conversation).contains("Java"));
        assertFalse(memory.nextStep(conversation).contains("java-backend:java"));
        db.update("UPDATE career_skill_sessions SET state = jsonb_set(jsonb_set(state, '{phase}', '\"TEST\"'), '{testTopics}', '[\"java-backend:java\"]') WHERE account_id=? AND assessment_id=?", owner, assessment);
        assertTrue(memory.nextStep(conversation).contains("Java"));
        assertFalse(memory.nextStep(conversation).contains("java-backend:java"));

        migration.importAssessment(owner, assessment);
        assertEquals(java.util.List.of("java", "java-backend:java"), db.queryForList("SELECT DISTINCT capability_id FROM capability_evidence WHERE account_id=? ORDER BY capability_id", String.class, owner));

    }

    @Test @org.springframework.transaction.annotation.Transactional void snapshotDoesNotMixDirectionAndRouteVersions() {
        var instrumented = org.mockito.Mockito.spy(db);
        var triggered = new java.util.concurrent.atomic.AtomicBoolean();
        org.mockito.Mockito.doAnswer(call -> {
            Object result = call.callRealMethod();
            String sql = call.getArgument(0);
            if (sql.contains("career_directions") && triggered.compareAndSet(false, true)) {
                db.update("UPDATE career_directions SET title='New title' WHERE id='java-backend'");
                db.update("INSERT INTO career_route_versions(direction_id,version) VALUES ('java-backend',2)");
                db.update("INSERT INTO career_route_items(direction_id,version,position,capability_id,title,objective) VALUES ('java-backend',2,1,'java-backend:java','New module','New objective')");
            }
            return result;
        }).when(instrumented).query(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<Object>>any(), org.mockito.ArgumentMatchers.<Object[]>any());
        var selection = new CareerDirections(instrumented).require("java-backend");
        assertTrue(triggered.get());
        assertEquals(1, selection.version());
        assertEquals("Java 后端开发", selection.direction().title());
        assertNotEquals("New module", selection.direction().skills().getFirst().title());
        org.mockito.Mockito.verify(instrumented, org.mockito.Mockito.times(1)).query(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<Object>>any(), org.mockito.ArgumentMatchers.<Object[]>any());
    }

    @Test @org.springframework.transaction.annotation.Transactional void claimUsesRouteUpdatedAfterEarlierRead() {
        long owner = db.queryForObject("INSERT INTO accounts(email,display_name,password_hash,role) VALUES ('claim-route@test.invalid','Test','hash','STUDENT') RETURNING id", Long.class);
        db.update("INSERT INTO resume_versions(account_id,version,draft_revision,content) VALUES (?,1,1,'{}')", owner);
        db.update("INSERT INTO mentor_main_conversations(account_id,first_resume_version) VALUES (?,1)", owner);
        var stale = directions.require("java-backend");
        var previous = writes.claim(owner, "java-backend", 1);
        db.update("INSERT INTO career_route_versions(direction_id,version) VALUES ('java-backend',2)");
        db.update("INSERT INTO career_route_items(direction_id,version,position,capability_id,title,objective) VALUES ('java-backend',2,1,'java-backend:java','New module','New objective')");
        assertEquals(1, stale.version());
        var claim = writes.claim(owner, "java-backend", 1);
        assertEquals(2, claim.selection().version());
        assertNotEquals(previous.assessment().id(), claim.assessment().id());
        assertEquals("New module", claim.selection().route().label("java-backend:java"));
    }

    @Test void claimWaitsForInProgressRouteUpdate() throws Exception {
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var locked = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        long owner = db.queryForObject("INSERT INTO accounts(email,display_name,password_hash,role) VALUES ('claim-race@test.invalid','Test','hash','STUDENT') RETURNING id", Long.class);
        db.update("INSERT INTO resume_versions(account_id,version,draft_revision,content) VALUES (?,1,1,'{}')", owner);
        db.update("INSERT INTO mentor_main_conversations(account_id,first_resume_version) VALUES (?,1)", owner);
        db.update("INSERT INTO career_directions(id,title,description,roles) VALUES ('claim-race','Original','Description','Roles')");
        db.update("INSERT INTO career_route_versions(direction_id,version) VALUES ('claim-race',1)");
        db.update("INSERT INTO career_route_items(direction_id,version,position,capability_id,title,objective) VALUES ('claim-race',1,1,'java-backend:java','Old','Old objective')");
        try {
            var update = pool.submit(() -> transactions.executeWithoutResult(status -> {
                db.update("UPDATE career_directions SET title='Updated' WHERE id='claim-race'");
                locked.countDown();
                try { if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                db.update("INSERT INTO career_route_versions(direction_id,version) VALUES ('claim-race',2)");
                db.update("INSERT INTO career_route_items(direction_id,version,position,capability_id,title,objective) VALUES ('claim-race',2,1,'java-backend:java','New','New objective')");
            }));
            assertTrue(locked.await(10, java.util.concurrent.TimeUnit.SECONDS));
            var claim = pool.submit(() -> writes.claim(owner, "claim-race", 1));
            assertThrows(java.util.concurrent.TimeoutException.class, () -> claim.get(300, java.util.concurrent.TimeUnit.MILLISECONDS));
            release.countDown();
            update.get(10, java.util.concurrent.TimeUnit.SECONDS);
            var result = claim.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(2, result.selection().version());
            assertEquals("New", result.selection().route().label("java-backend:java"));
        } finally {
            release.countDown();
            pool.shutdownNow();
            pool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);
            db.update("DELETE FROM accounts WHERE id=?", owner);
            db.update("DELETE FROM career_route_items WHERE direction_id='claim-race'");
            db.update("DELETE FROM career_route_versions WHERE direction_id='claim-race'");
            db.update("DELETE FROM career_directions WHERE id='claim-race'");
        }
    }

}
