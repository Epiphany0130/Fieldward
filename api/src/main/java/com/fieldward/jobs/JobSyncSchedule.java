package com.fieldward.jobs;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class JobSyncSchedule {
    private static final Logger log = LoggerFactory.getLogger(JobSyncSchedule.class);
    private final JobSyncService service;
    private final List<JobSource> sources;
    private final JobSourceRegistry registry;
    JobSyncSchedule(JobSyncService service, List<JobSource> sources, JobSourceRegistry registry) { this.service = service; this.sources = sources; this.registry = registry; }
    @Scheduled(fixedDelayString = "${fieldward.jobs.sync-interval-ms:3600000}", initialDelayString = "${fieldward.jobs.sync-initial-delay-ms:60000}")
    void syncAll() {
        registry.reconcile();
        for (var source : sources) {
            if (!source.authorized()) continue;
            try { service.sync(source.id()); }
            catch (RuntimeException e) { log.warn("Job source {} failed to sync", source.id(), e); }
        }
    }
}
