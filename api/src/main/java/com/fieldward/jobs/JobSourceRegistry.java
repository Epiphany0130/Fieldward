package com.fieldward.jobs;

import java.util.List;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class JobSourceRegistry {
    private final JobMapper mapper;
    private final List<JobSource> sources;
    JobSourceRegistry(JobMapper mapper, List<JobSource> sources) { this.mapper = mapper; this.sources = sources; }
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void reconcile() {
        mapper.revokeAllSources();
        for (var source : sources) {
            boolean authorized = source.authorized() && source.allowedHosts() != null && !source.allowedHosts().isEmpty();
            mapper.register(source.id(), authorized);
        }
    }
}
