package com.fieldward.jobs;

import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class JobMatchWorker {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(JobMatchWorker.class);
    private final JobMatchStore store;
    private final JobRequirementExtractor extractor;
    private final java.util.List<JobSource> sources;
    JobMatchWorker(JobMatchStore store, JobRequirementExtractor extractor, java.util.List<JobSource> sources, @org.springframework.beans.factory.annotation.Value("${fieldward.jobs.match-enabled:false}") boolean enabled) { this.store=store; this.extractor=extractor; this.sources=sources; this.enabled=enabled; }
    private final boolean enabled;
    @Scheduled(fixedDelayString="${fieldward.jobs.match-interval-ms:5000}", initialDelayString="${fieldward.jobs.match-initial-delay-ms:60000}")
    void process() {
        if (!enabled) return;
        var claim=store.claim();
        if (claim == null) return;
        try {
            if (!store.permitted(claim,sources)) { store.fail(claim); return; }
            var requirements=extractor.extract(store.text(claim.version()),store.catalog());
            if (store.permitted(claim,sources)) store.complete(claim,requirements); else store.fail(claim);
        }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); store.fail(claim); }
        catch (Exception e) { log.warn("Job requirement extraction failed for version {}",claim.version()); store.fail(claim); }
    }
}
