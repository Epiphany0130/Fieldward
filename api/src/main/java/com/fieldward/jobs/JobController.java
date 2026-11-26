package com.fieldward.jobs;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
class JobController {
    private final JobMapper mapper;
    private final List<JobSource> sources;
    JobController(JobMapper mapper, List<JobSource> sources) { this.mapper = mapper; this.sources = sources; }
    @GetMapping List<JobMapper.JobView> list() { return mapper.listActive().stream().filter(job -> sources.stream().anyMatch(source -> source.id().equals(job.sourceId()) && JobSourcePolicy.permits(source, job.sourceUrl()))).toList(); }
}
