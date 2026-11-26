package com.fieldward.mentor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CapabilityService {
    private static final Set<String> SOURCES = Set.of("SELF_REPORT", "DIAGNOSIS", "LEARNING", "INTERVIEW",
            "LEGACY_ASSESSMENT", "LEGACY_VERIFICATION");
    private static final Set<String> OUTCOMES = Set.of("PENDING", "PASSED", "NEEDS_PRACTICE");
    private final CapabilityMapper mapper;

    CapabilityService(CapabilityMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public Evidence recordEvidence(long accountId, String capabilityId, EvidenceInput input) {
        if (input == null || input.sourceType() == null || !SOURCES.contains(input.sourceType())
                || input.sourceId() == null || input.sourceId().isBlank()
                || input.outcome() == null || !OUTCOMES.contains(input.outcome())
                || ("SELF_REPORT".equals(input.sourceType()) && !"PENDING".equals(input.outcome()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无效的能力证据来源或结论");
        }
        mapper.insert(accountId, capabilityId, input);
        var evidence = mapper.find(accountId, capabilityId, input);
        if (!evidence.outcome().equals(input.outcome())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该来源已有不同的能力记录");
        }
        return evidence;
    }

    @Transactional(readOnly = true)
    public RouteSnapshot currentRoute(long accountId, String directionId) {
        var version = mapper.currentVersion(directionId);
        if (version == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到该方向的学习路线");
        var evidence = mapper.routeEvidence(accountId, directionId, version).stream()
                .collect(Collectors.groupingBy(Evidence::capabilityId));
        var capabilities = mapper.items(directionId, version).stream()
                .map(item -> new RouteCapability(item.capabilityId(), item.title(), item.objective(),
                        evidence.getOrDefault(item.capabilityId(), List.of()))).toList();
        return new RouteSnapshot(directionId, version, capabilities);
    }

    public List<Evidence> history(long accountId, String capabilityId) {
        return mapper.history(accountId, capabilityId);
    }

    public record EvidenceInput(String sourceType, String sourceId, String outcome) {}
    public record Evidence(long id, String capabilityId, String sourceType, String sourceId, String outcome,
                           OffsetDateTime createdAt) {}
    public record RouteCapability(String capabilityId, String title, String objective, List<Evidence> evidence) {}
    public record RouteSnapshot(String directionId, int version, List<RouteCapability> capabilities) {}
}
