package com.fieldward.jobs;

import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/jobs")
class JobAdminController {
    private final JobMapper mapper;
    private final JobSyncService service;
    private final List<JobSource> sources;
    JobAdminController(JobMapper mapper, JobSyncService service, List<JobSource> sources) { this.mapper = mapper; this.service = service; this.sources = sources; }
    @GetMapping("/sources") List<String> sources() { return sources.stream().filter(JobSource::authorized).map(JobSource::id).toList(); }
    @GetMapping List<JobMapper.JobView> list() { return mapper.list(); }
    @PostMapping("/sources/{sourceId}/sync") JobSyncService.SyncResult sync(@PathVariable String sourceId) { return service.sync(sourceId); }
    @PostMapping("/{id}/remove") void remove(@PathVariable long id) { service.remove(id); }
}
