package com.fieldward.jobs;

import java.util.List;

public interface JobSource {
    String id();
    boolean authorized();
    java.util.Set<String> allowedHosts();
    SourceBatch fetchChanges(String cursor);
    record SourceBatch(String cursor, List<SourceJob> jobs) {}
    record SourceJob(String sourceJobId, String title, String requirements, String sourceUrl, boolean active) {
        public SourceJob(String sourceJobId, String title, String requirements, String sourceUrl) {
            this(sourceJobId, title, requirements, sourceUrl, true);
        }
    }
}
