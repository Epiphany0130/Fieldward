package com.fieldward.jobs;

import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ApplicationService {
    private static final Set<String> STATUSES = Set.of("INTERESTED", "APPLIED", "INTERVIEW", "OFFER", "REJECTED", "WITHDRAWN");
    private final ApplicationStore store;
    private final JobMapper jobs;
    private final List<JobSource> sources;
    ApplicationService(ApplicationStore store, JobMapper jobs, List<JobSource> sources) { this.store=store; this.jobs=jobs; this.sources=sources; }
    private void requireJob(long jobId) {
        if (jobs.listActive().stream().noneMatch(job -> job.id() == jobId && sources.stream().anyMatch(source -> source.id().equals(job.sourceId()) && JobSourcePolicy.permits(source, job.sourceUrl()))))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到可用岗位");
    }
    @Transactional public void favorite(long accountId, long jobId, boolean enabled) { requireJob(jobId); store.favorite(accountId, jobId, enabled); }
    @Transactional public ApplicationStore.View updateProgress(long accountId, long jobId, String status, String requestId) {
        if (!STATUSES.contains(status) || requestId == null || requestId.isBlank() || requestId.length() > 200 || requestId.indexOf(0) >= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无效的进度或请求编号");
        requireJob(jobId);
        return store.updateProgress(accountId, jobId, status, requestId);
    }
    public boolean isApplicationMessage(String message) { return ApplicationMessage.isProgressStatement(message); }
    @Transactional public String updateFromMessage(long accountId, String message, String requestId) {
        if (!ApplicationMessage.isProgressStatement(message)) return null;
        var available = jobs.listActive().stream().filter(job -> sources.stream().anyMatch(source -> source.id().equals(job.sourceId()) && JobSourcePolicy.permits(source,job.sourceUrl()))).toList();
        var intent = ApplicationMessage.parse(message, available);
        if (intent == null) return "请明确要更新进度的岗位名称；如有同名岗位，请在岗位页面更新。";
        updateProgress(accountId, intent.jobId(), intent.status(), requestId);
        String title = available.stream().filter(job -> job.id() == intent.jobId()).findFirst().orElseThrow().title();
        return "已更新「" + title + "」的投递进度：" + switch (intent.status()) { case "APPLIED" -> "已投递"; case "INTERVIEW" -> "面试中"; case "OFFER" -> "已获录用"; case "REJECTED" -> "未通过"; default -> "已撤回"; } + "。";
    }
    List<ApplicationStore.View> list(long accountId) {
        var available = jobs.listActive().stream().filter(job -> sources.stream().anyMatch(source -> source.id().equals(job.sourceId()) && JobSourcePolicy.permits(source,job.sourceUrl()))).map(JobMapper.JobView::id).collect(java.util.stream.Collectors.toSet());
        return store.list(accountId).stream().filter(entry -> available.contains(entry.jobId())).toList();
    }
}
