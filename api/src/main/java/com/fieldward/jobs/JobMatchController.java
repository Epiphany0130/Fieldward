package com.fieldward.jobs;

import com.fieldward.account.AccountPrincipal;
import java.util.Comparator;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs/matches")
class JobMatchController {
    private final JobMapper mapper;
    private final JobMatchStore store;
    private final List<JobSource> sources;
    JobMatchController(JobMapper mapper, JobMatchStore store, List<JobSource> sources) { this.mapper=mapper; this.store=store; this.sources=sources; }
    @GetMapping List<Entry> list(@AuthenticationPrincipal AccountPrincipal principal) {
        return mapper.listActiveVersioned().stream()
                .filter(item -> sources.stream().anyMatch(source -> source.id().equals(item.job().sourceId()) && JobSourcePolicy.permits(source,item.job().sourceUrl())))
                .map(item -> new Entry(item.job(),store.match(principal.accountId(),item.version())))
                .sorted(Comparator.comparing((Entry entry) -> entry.match().result().percent(),Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }
    record Entry(JobMapper.JobView job, JobMatchStore.View match) {}
}
