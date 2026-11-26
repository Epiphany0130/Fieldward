package com.fieldward.mentor;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DiagnosisServiceTest {
    @Test
    void providerChoosesRelevantModulesAndAdaptsWithoutEarlyFeedback() {
        var provider = new DiagnosisService.Provider() {
            public DiagnosisService.Plan plan(String direction, String resume, List<String> allowed) {
                assertThat(direction).isEqualTo("java-backend");
                assertThat(resume).contains("Spring Boot");
                return new DiagnosisService.Plan(List.of("java-backend:api"), "请介绍你的项目接口设计");
            }
            public DiagnosisService.Next followUp(List<String> modules, String answer) {
                assertThat(answer).contains("缓存");
                return new DiagnosisService.Next("缓存失效时你如何处理？", false);
            }
        };
        var service = new DiagnosisService(provider);
        var state = service.begin("java-backend", "Spring Boot 项目", List.of("java-backend:api", "sql"));
        assertThat(state.modules()).containsExactly("java-backend:api");
        assertThat(service.answer(state, "使用缓存").question()).contains("缓存失效");
    }

    @Test
    void hidesLegacyMatchBeforeDiagnosis() {
        var report = new CareerFit.Report(80, CareerFit.Stage.INTERVIEW_READY, 60, true,
                CareerFit.SourceQuality.READABLE, List.of(), List.of(), 4, null, java.util.Map.of());
        assertThat(report.withoutMatch().matchPercent()).isNull();
        assertThat(report.withoutMatch().stage()).isEqualTo(CareerFit.Stage.INTERVIEW_READY);
        assertThat(report.withoutMatch().coreCovered()).isTrue();
    }

    @Test
    void rejectsProviderModulesOutsideTheSelectedRoute() {
        var service = new DiagnosisService(new DiagnosisService.Provider() {
            public DiagnosisService.Plan plan(String d, String r, List<String> allowed) {
                return new DiagnosisService.Plan(List.of("unrelated"), "question");
            }
            public DiagnosisService.Next followUp(List<String> modules, String answer) { throw new AssertionError(); }
        });
        assertThatThrownBy(() -> service.begin("java-backend", "resume", List.of("sql")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
