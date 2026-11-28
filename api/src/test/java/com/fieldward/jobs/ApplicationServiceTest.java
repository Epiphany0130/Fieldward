package com.fieldward.jobs;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApplicationServiceTest {
    private final ApplicationStore store = mock(ApplicationStore.class);
    private final JobMapper jobs = mock(JobMapper.class);
    private final JobSource source = new JobSource() {
        public String id() { return "ok"; }
        public boolean authorized() { return true; }
        public Set<String> allowedHosts() { return Set.of("example.com"); }
        public SourceBatch fetchChanges(String cursor) { throw new UnsupportedOperationException(); }
    };
    private final ApplicationService service = new ApplicationService(store, jobs, List.of(source));
    private JobMapper.JobView job(String url) { return new JobMapper.JobView(1,"ok","a","ACTIVE","岗位","要求",url,null); }

    @Test void favoritesOnlyAuthorizedJobsForCurrentAccount() {
        when(jobs.listActive()).thenReturn(List.of(job("https://example.com/job")));
        service.favorite(7, 1, true);
        verify(store).favorite(7, 1, true);
        when(jobs.listActive()).thenReturn(List.of(job("https://evil.test/job")));
        assertThatThrownBy(() -> service.favorite(7, 1, true)).isInstanceOf(RuntimeException.class);
        verifyNoMoreInteractions(store);
    }

    @Test void chatUpdatesOnlyUnambiguousAuthorizedJob() {
        when(jobs.listActive()).thenReturn(List.of(job("https://example.com/job")));
        assertThat(service.updateFromMessage(7, "我已投递岗位", "mentor-turn:1")).contains("更新");
        verify(store).updateProgress(7, 1, "APPLIED", "mentor-turn:1");
        assertThat(service.updateFromMessage(7, "我已投递", "mentor-turn:2")).contains("明确");
        assertThat(service.updateFromMessage(7, "我可能投递岗位", "mentor-turn:3")).isNull();
        when(jobs.listActive()).thenReturn(List.of(job("https://example.com/job"),job("https://example.com/other")));
        assertThat(service.updateFromMessage(7, "我已投递岗位", "mentor-turn:4")).contains("明确");
        verifyNoMoreInteractions(store);
    }

    @Test void manualProgressDoesNotRequireCreditsAndPassesIdempotencyKey() {
        when(jobs.listActive()).thenReturn(List.of(job("https://example.com/job")));
        service.updateProgress(7, 1, "INTERVIEW", "request-1");
        verify(store).updateProgress(7, 1, "INTERVIEW", "request-1");
        assertThatThrownBy(() -> service.updateProgress(7, 1, "INVALID", "request-2")).isInstanceOf(RuntimeException.class);
        verifyNoMoreInteractions(store);
    }
}
