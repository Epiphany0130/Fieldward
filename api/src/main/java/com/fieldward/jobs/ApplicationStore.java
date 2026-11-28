package com.fieldward.jobs;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

@Repository
class ApplicationStore {
    private final JdbcTemplate db;
    ApplicationStore(JdbcTemplate db) { this.db=db; }
    void favorite(long account, long job, boolean enabled) {
        db.update("INSERT INTO job_applications(account_id,job_id,favorite) VALUES (?,?,?) ON CONFLICT(account_id,job_id) DO UPDATE SET favorite=EXCLUDED.favorite,updated_at=now()",account,job,enabled);
    }
    View updateProgress(long account, long job, String status, String request) {
        int inserted = db.update("INSERT INTO application_requests(account_id,request_id,job_id,status) VALUES (?,?,?,?) ON CONFLICT DO NOTHING",account,request,job,status);
        var previous = db.queryForObject("SELECT job_id,status FROM application_requests WHERE account_id=? AND request_id=?",(rs,n)->new Request(rs.getLong(1),rs.getString(2)),account,request);
        if (previous.job()!=job || !previous.status().equals(status)) throw new ResponseStatusException(HttpStatus.CONFLICT,"请求编号已用于其他进度");
        if (inserted == 1) db.update("INSERT INTO job_applications(account_id,job_id,status) VALUES (?,?,?) ON CONFLICT(account_id,job_id) DO UPDATE SET status=EXCLUDED.status,updated_at=now()",account,job,status);
        return list(account).stream().filter(view -> view.jobId()==job).findFirst().orElseThrow();
    }
    List<View> list(long account) { return db.query("SELECT job_id,favorite,status,updated_at FROM job_applications WHERE account_id=? ORDER BY updated_at DESC",(rs,n)->new View(rs.getLong(1),rs.getBoolean(2),rs.getString(3),rs.getObject(4,OffsetDateTime.class)),account); }
    record Request(long job,String status) {}
    public record View(long jobId,boolean favorite,String status,OffsetDateTime updatedAt) {}
}
