package com.fieldward.jobs;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class JobSyncService {
    private final JobMapper mapper;
    private final List<JobSource> sources;
    JobSyncService(JobMapper mapper, List<JobSource> sources) { this.mapper = mapper; this.sources = sources; }

    @Transactional
    public SyncResult sync(String sourceId) {
        JobSource source = sources.stream().filter(s -> s.id().equals(sourceId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到岗位来源"));
        if (!source.authorized() || source.allowedHosts() == null || source.allowedHosts().isEmpty())
            throw new IllegalStateException("Job source is not authorized");
        String cursor = mapper.lockCursor(sourceId);
        JobSource.SourceBatch batch = source.fetchChanges(cursor);
        if (batch == null || batch.jobs() == null) throw new IllegalStateException("Invalid source response");
        for (var job : batch.jobs()) {
            if (job == null || job.sourceJobId() == null || job.sourceJobId().isBlank() || job.title() == null || job.title().isBlank() || job.requirements() == null || job.sourceUrl() == null) throw new IllegalStateException("Invalid source job");
            if (!JobSourcePolicy.permits(source, job.sourceUrl()))
                throw new IllegalStateException("Job source URL is not allowed");
            mapper.save(sourceId, job);
        }
        mapper.advance(sourceId, batch.cursor());
        return new SyncResult(batch.jobs().size(), batch.cursor());
    }
    @Transactional
    public void remove(long id) {
        if (mapper.remove(id) == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到岗位");
    }
    public record SyncResult(int received, String cursor) {}
}
