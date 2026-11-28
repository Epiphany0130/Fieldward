package com.fieldward.jobs;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class JobMatchServiceTest {
    private final JobMatchService service = new JobMatchService();
    private final List<JobMatchService.Requirement> requirements = List.of(
            new JobMatchService.Requirement("sql", "SQL", "熟悉 SQL"),
            new JobMatchService.Requirement("java", "Java", "使用 Java 开发"));

    @Test void diagnosisIsRequiredAndMissingRequirementsDoNotProduceFictitiousScores() {
        assertThat(service.calculate(false, requirements, List.of()).percent()).isNull();
        assertThat(service.calculate(true, List.of(), List.of()).percent()).isNull();
    }

    @Test void onlyVerifiedEvidenceCountsAndResultsRetainSourceTrace() {
        var evidence = List.of(new JobMatchService.Evidence(1, "sql", "LEARNING", "lesson-1", "PASSED"),
                new JobMatchService.Evidence(2, "java", "SELF_REPORT", "resume-1", "PENDING"));
        var result = service.calculate(true, requirements, evidence);
        assertThat(result.percent()).isEqualTo(50);
        assertThat(result.matched()).extracting(JobMatchService.Matched::evidenceId).containsExactly(1L);
        assertThat(result.pending()).extracting(JobMatchService.Requirement::capabilityId).containsExactly("java");
        assertThat(result.matched().getFirst().sourceId()).isEqualTo("lesson-1");
        assertThat(result.matched().getFirst().requirement().sourceText()).isEqualTo("熟悉 SQL");
    }

    @Test void latestEvidenceControlsStatusAndDuplicatesDoNotIncreaseScore() {
        var evidence = List.of(new JobMatchService.Evidence(1, "sql", "LEARNING", "1", "PASSED"),
                new JobMatchService.Evidence(2, "sql", "INTERVIEW", "2", "NEEDS_PRACTICE"));
        var result = service.calculate(true, requirements, evidence);
        assertThat(result.percent()).isZero();
        assertThat(result.missing()).hasSize(2);
    }
    @Test void extractionRejectsUngroundedAndUnknownCapabilities() {
        var extractor = new JobRequirementExtractor(null, null);
        var input = List.of(new JobMatchService.Requirement("sql", "SQL", "熟悉 SQL"),
                new JobMatchService.Requirement("made-up", "Java", "掌握 Java"));
        assertThat(extractor.validate("熟悉 SQL；掌握 Java", input, java.util.Set.of("sql")))
                .extracting(JobMatchService.Requirement::capabilityId).containsExactly("sql", null);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> extractor.validate("熟悉 SQL", input, java.util.Set.of("sql"))))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void unmappedRequirementsStayPending() {
        var result = service.calculate(true, List.of(new JobMatchService.Requirement(null, "项目经验", "有项目经验")), List.of());
        assertThat(result.percent()).isZero();
        assertThat(result.pending()).hasSize(1);
        assertThat(result.missing()).isEmpty();
    }
    @Test void extractsWithExistingModelAndValidatesSource() throws Exception {
        var model = org.mockito.Mockito.mock(com.fieldward.mentor.MentorModel.class);
        var json = new tools.jackson.databind.json.JsonMapper();
        org.mockito.Mockito.when(model.completeJson(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(json.readTree("{\"requirements\":[{\"capabilityId\":\"sql\",\"title\":\"SQL\",\"sourceText\":\"熟悉 SQL\"}]}"));
        var extractor = new JobRequirementExtractor(model, json);
        assertThat(extractor.extract("熟悉 SQL", java.util.Map.of("sql", "SQL"))).hasSize(1);
    }
}
