package com.fieldward.jobs;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JobMapper {
    private final JdbcTemplate db;
    JobMapper(JdbcTemplate db) { this.db = db; }

    void revokeAllSources() { db.update("UPDATE job_sources SET authorized=false"); }
    void register(String id, boolean authorized) {
        db.update("INSERT INTO job_sources(id,name,authorized) VALUES (?,?,?) ON CONFLICT(id) DO UPDATE SET authorized=EXCLUDED.authorized", id, id, authorized);
    }
    String lockCursor(String sourceId) {
        var rows = db.query("SELECT cursor FROM job_sources WHERE id=? AND authorized=true FOR UPDATE", (rs, n) -> new Cursor(rs.getString(1)), sourceId);
        if (rows.isEmpty()) throw new IllegalArgumentException("Unknown job source");
        return rows.getFirst().value();
    }
    void save(String sourceId, JobSource.SourceJob job) {
        long id = db.queryForObject("""
            INSERT INTO jobs(source_id, source_job_id) VALUES (?, ?)
            ON CONFLICT (source_id, source_job_id) DO UPDATE SET source_job_id=EXCLUDED.source_job_id
            RETURNING id
            """, Long.class, sourceId, job.sourceJobId());
        db.update("UPDATE jobs SET status=CASE WHEN status='REMOVED' THEN status WHEN ? THEN 'ACTIVE' ELSE 'SOURCE_CLOSED' END WHERE id=?", job.active(), id);
        db.update("""
            INSERT INTO job_versions(job_id,title,requirements,source_url)
            SELECT ?,?,?,? WHERE NOT EXISTS (
              SELECT 1 FROM (SELECT title, requirements, source_url FROM job_versions WHERE job_id=? ORDER BY id DESC LIMIT 1) latest
              WHERE title=? AND requirements=? AND source_url=?)
            """, id, job.title(), job.requirements(), job.sourceUrl(), id, job.title(), job.requirements(), job.sourceUrl());
        db.update("UPDATE job_versions SET verified_at=now() WHERE id=(SELECT id FROM job_versions WHERE job_id=? ORDER BY id DESC LIMIT 1)", id);
    }
    void advance(String sourceId, String cursor) {
        db.update("UPDATE job_sources SET cursor=?, last_verified_at=now() WHERE id=?", cursor, sourceId);
    }
    int remove(long id) { return db.update("UPDATE jobs SET status='REMOVED' WHERE id=?", id); }
    List<JobView> list() {
        return db.query("""
          SELECT j.id,j.source_id,j.source_job_id,j.status,v.title,v.requirements,v.source_url,v.verified_at
          FROM jobs j JOIN LATERAL (SELECT * FROM job_versions WHERE job_id=j.id ORDER BY id DESC LIMIT 1) v ON true
          ORDER BY j.id DESC
          """, (rs, n) -> new JobView(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getObject(8, java.time.OffsetDateTime.class)));
    }
    List<JobView> listActive() {
        return db.query("""
          SELECT j.id,j.source_id,j.source_job_id,j.status,v.title,v.requirements,v.source_url,v.verified_at
          FROM jobs j JOIN LATERAL (SELECT * FROM job_versions WHERE job_id=j.id ORDER BY id DESC LIMIT 1) v ON true
          WHERE j.status='ACTIVE' AND EXISTS (SELECT 1 FROM job_sources s WHERE s.id=j.source_id AND s.authorized) ORDER BY j.id DESC
          """, (rs, n) -> new JobView(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getObject(8, java.time.OffsetDateTime.class)));
    }
    List<VersionedJob> listActiveVersioned() {
        return db.query("""
          SELECT j.id,j.source_id,j.source_job_id,j.status,v.title,v.requirements,v.source_url,v.verified_at,v.id
          FROM jobs j JOIN LATERAL (SELECT * FROM job_versions WHERE job_id=j.id ORDER BY id DESC LIMIT 1) v ON true
          WHERE j.status='ACTIVE' AND EXISTS (SELECT 1 FROM job_sources s WHERE s.id=j.source_id AND s.authorized) ORDER BY j.id DESC
          """, (rs,n) -> new VersionedJob(new JobView(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getObject(8,java.time.OffsetDateTime.class)),rs.getLong(9)));
    }
    record VersionedJob(JobView job,long version) {}
    record Cursor(String value) {}
    record JobView(long id, String sourceId, String sourceJobId, String status, String title, String requirements, String sourceUrl, java.time.OffsetDateTime verifiedAt) {}
}
