package com.fieldward.jobs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Computes only from explicitly extracted requirements and account-owned evidence. */
@Service
class JobMatchService {
    Match calculate(boolean diagnosed, List<Requirement> requirements, List<Evidence> evidence) {
        if (!diagnosed || requirements.isEmpty()) return new Match(null, List.of(), List.of(), List.of());
        Map<String, Evidence> latest = evidence.stream().collect(Collectors.toMap(Evidence::capabilityId,
                Function.identity(), (a, b) -> a.id() > b.id() ? a : b));
        var matched = new ArrayList<Matched>();
        var pending = new ArrayList<Requirement>();
        var missing = new ArrayList<Requirement>();
        for (var requirement : requirements) {
            if (requirement.capabilityId() == null) { pending.add(requirement); continue; }
            var item = latest.get(requirement.capabilityId());
            if (item == null || "NEEDS_PRACTICE".equals(item.outcome())) missing.add(requirement);
            else if ("PASSED".equals(item.outcome()) && !"SELF_REPORT".equals(item.sourceType()))
                matched.add(new Matched(requirement, item.id(), item.sourceType(), item.sourceId()));
            else pending.add(requirement);
        }
        return new Match((int) Math.round(100.0 * matched.size() / requirements.size()),
                List.copyOf(matched), List.copyOf(pending), List.copyOf(missing));
    }
    record Requirement(String capabilityId, String title, String sourceText) {}
    record Evidence(long id, String capabilityId, String sourceType, String sourceId, String outcome) {}
    record Matched(Requirement requirement, long evidenceId, String sourceType, String sourceId) {}
    record Match(Integer percent, List<Matched> matched, List<Requirement> pending, List<Requirement> missing) {}
}
