package com.fieldward.jobs;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
class JobMatchStore {
    private final JdbcTemplate db;
    private final JsonMapper json;
    private final JobMatchService service;
    JobMatchStore(JdbcTemplate db, JsonMapper json, JobMatchService service) { this.db = db; this.json = json; this.service = service; }
    void saveRequirements(long version, List<JobMatchService.Requirement> requirements) {
        db.update("""
            INSERT INTO job_requirement_snapshots(job_version_id,status,requirements,updated_at) VALUES (?,'READY',CAST(? AS jsonb),now())
            ON CONFLICT(job_version_id) DO UPDATE SET status='READY',requirements=EXCLUDED.requirements,lease_until=NULL,updated_at=now()
            """, version, json.writeValueAsString(requirements));
    }
    View match(long account, long version) {
        boolean diagnosed = Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM diagnosis_reports WHERE account_id=?)", Boolean.class, account));
        if (!diagnosed) return new View("DIAGNOSIS_REQUIRED", version, 0, null, service.calculate(false, List.of(), List.of()));
        enqueue(version);
        expireExhausted();
        var snapshot = db.query("SELECT status,requirements::text,updated_at FROM job_requirement_snapshots WHERE job_version_id=?", (r,n)->new Snapshot(r.getString(1),r.getString(2),r.getObject(3,OffsetDateTime.class)),version).getFirst();
        if (!snapshot.status().equals("READY")) return new View(snapshot.status(),version,0,snapshot.updatedAt(),service.calculate(true,List.of(),List.of()));
        var requirements = List.of(json.readValue(snapshot.requirements(), JobMatchService.Requirement[].class));
        var evidence = db.query("SELECT id,capability_id,source_type,source_id,outcome FROM capability_evidence WHERE account_id=? ORDER BY id", (r,n)->new JobMatchService.Evidence(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5)),account);
        return new View("UNVERIFIED",version,evidence.stream().mapToLong(JobMatchService.Evidence::id).max().orElse(0),OffsetDateTime.now(),new JobMatchService.Match(null,List.of(),requirements,List.of()));
    }
    void enqueue(long version) {
        db.update("INSERT INTO job_requirement_snapshots(job_version_id,status) VALUES (?,'PENDING') ON CONFLICT DO NOTHING",version);
    }
    void expireExhausted() {
        db.update("UPDATE job_requirement_snapshots SET status='FAILED',lease_until=NULL,lease_token=NULL,updated_at=now() WHERE status='PROCESSING' AND attempts>=3 AND lease_until<now()");
    }
    Claim claim() {
        expireExhausted();
        var token = java.util.UUID.randomUUID();
        var rows = db.query("""
            UPDATE job_requirement_snapshots SET status='PROCESSING',lease_token=?,lease_until=now()+interval '2 minutes',attempts=attempts+1,updated_at=now()
            WHERE job_version_id=(SELECT job_version_id FROM job_requirement_snapshots
              WHERE attempts < 3 AND (status='PENDING' OR (status IN ('FAILED','PROCESSING') AND lease_until < now()))
              AND EXISTS (SELECT 1 FROM job_versions v JOIN jobs j ON j.id=v.job_id JOIN job_sources s ON s.id=j.source_id
                WHERE v.id=job_version_id AND j.status='ACTIVE' AND s.authorized
                  AND v.id=(SELECT max(v2.id) FROM job_versions v2 WHERE v2.job_id=j.id))
              ORDER BY updated_at FOR UPDATE SKIP LOCKED LIMIT 1)
            RETURNING job_version_id
            """, (r,n)->r.getLong(1),token);
        return rows.isEmpty() ? null : new Claim(rows.getFirst(),token);
    }
    void fail(Claim claim) {
        db.update("UPDATE job_requirement_snapshots SET status='FAILED',lease_until=now()+interval '1 minute',lease_token=NULL,updated_at=now() WHERE job_version_id=? AND lease_token=?",claim.version(),claim.token());
    }
    void complete(Claim claim, List<JobMatchService.Requirement> requirements) {
        db.update("UPDATE job_requirement_snapshots SET status='READY',requirements=CAST(? AS jsonb),lease_until=NULL,lease_token=NULL,updated_at=now() WHERE job_version_id=? AND lease_token=? AND EXISTS (SELECT 1 FROM job_versions v JOIN jobs j ON j.id=v.job_id JOIN job_sources s ON s.id=j.source_id WHERE v.id=job_version_id AND j.status='ACTIVE' AND s.authorized AND v.id=(SELECT max(v2.id) FROM job_versions v2 WHERE v2.job_id=j.id))",json.writeValueAsString(requirements),claim.version(),claim.token());
    }
    String text(long version) { return db.queryForObject("SELECT requirements FROM job_versions WHERE id=?", String.class,version); }
    java.util.Map<String,String> catalog() {
        var result = new java.util.LinkedHashMap<String,String>();
        db.query("SELECT id,title FROM career_capabilities ORDER BY id", (org.springframework.jdbc.core.RowCallbackHandler) r -> result.put(r.getString(1),r.getString(2)));
        return result;
    }
    boolean permitted(Claim claim, List<JobSource> sources) {
        return db.query("SELECT j.source_id,v.source_url FROM job_versions v JOIN jobs j ON j.id=v.job_id JOIN job_sources s ON s.id=j.source_id WHERE v.id=? AND j.status='ACTIVE' AND s.authorized AND v.id=(SELECT max(v2.id) FROM job_versions v2 WHERE v2.job_id=j.id)",
            (r,n)-> { String id=r.getString(1), url=r.getString(2); return sources.stream().anyMatch(source -> source.id().equals(id) && JobSourcePolicy.permits(source,url)); },claim.version()).stream().findFirst().orElse(false);
    }
    record Claim(long version, java.util.UUID token) {}
    record Snapshot(String status,String requirements,OffsetDateTime updatedAt) {}
    record View(String status,long jobVersion,long evidenceVersion,OffsetDateTime updatedAt,JobMatchService.Match result) {}
}
