package com.fieldward.mentor;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
class CareerPlans {
    private static final Logger log = LoggerFactory.getLogger(CareerPlans.class);
    private final CareerMapper careers;
    private final LegacyCareerBackfill legacy;
    private final MentorConversationMapper conversations;
    private final MentorResumeMapper resumes;
    private final CareerWrites writes;
    private final CareerFit fit;
    private final MentorModel model;
    private final JsonMapper json;
    private final java.util.concurrent.ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();

    CareerPlans(CareerMapper careers, MentorConversationMapper conversations, MentorResumeMapper resumes,
                CareerWrites writes, CareerFit fit, MentorModel model, JsonMapper json, LegacyCareerBackfill legacy) {
        this.careers = careers;
        this.legacy = legacy;
        this.conversations = conversations;
        this.resumes = resumes;
        this.writes = writes;
        this.fit = fit;
        this.model = model;
        this.json = json;
    }

    State state(long accountId) {
        var workspace = conversations.findWorkspace(accountId);
        var directions = CareerCatalog.DIRECTIONS.stream().map(direction -> new Option(direction.id(), direction.title(),
                direction.description(), direction.roles(), direction.skills().stream().limit(3).map(CareerCatalog.Skill::title).toList())).toList();
        if (workspace == null) {
            return new State(Long.toString(accountId), null, null, false, null, directions, CareerCatalog.SOURCE);
        }
        var document = resumes.catalog(Long.parseLong(workspace.conversationId()), null, 1).getFirst();
        return new State(Long.toString(accountId), workspace.latestResumeVersion(), workspace.resumeName(),
                writes.waitingForText(document.readable(), document.ocrStatus()), current(accountId),
                directions, CareerCatalog.SOURCE);
    }

    Summary start(long accountId, long resumeVersion, String directionId) {
        var direction = CareerCatalog.require(directionId);
        var claim = writes.claim(accountId, directionId, resumeVersion);
        if (claim.start()) {
            try {
                workers.execute(() -> analyze(claim, direction));
            } catch (RejectedExecutionException exception) {
                writes.fail(claim.assessment());
            }
        }
        return summary(careers.current(accountId));
    }

    private void analyze(CareerWrites.Claim claim, CareerCatalog.Direction direction) {
        var assessment = claim.assessment();
        try {
            if (!claim.input().text().isBlank() && !model.available()) throw new IOException("Mentor model unavailable");
            writes.finish(claim, fit.analyze(direction, claim.input()));
            legacy.tryImport(assessment.accountId(), assessment.id());
        } catch (InterruptedException exception) {
            writes.fail(assessment);
            Thread.currentThread().interrupt();
        } catch (IOException exception) {
            log.warn("Career assessment {} failed: {}", assessment.id(), exception.getMessage());
            writes.fail(assessment);
        } catch (RuntimeException exception) {
            log.warn("Career assessment {} failed ({})", assessment.id(), exception.getClass().getSimpleName());
            writes.fail(assessment);
        }
    }

    Summary current(long accountId) {
        var stored = careers.current(accountId);
        if (stored != null) legacy.tryImport(accountId, stored.id());
        return summary(stored);
    }

    Summary archived(long accountId, long id) {
        var stored = careers.find(accountId, id);
        if (stored != null) legacy.tryImport(accountId, id);
        return summary(stored);
    }

    boolean needsOnboarding(long conversation) {
        return careers.needsOnboarding(conversation);
    }

    String guidance(long conversation) {
        var active = summary(careers.currentForConversation(conversation));
        if (active == null || active.report() == null) return "";
        var report = active.report();
        return "以下结构化学习数据来自工作台。根据当前目标和用户回答，用一次一个问题或小练习的方式继续引导。"
                + "技能掌握程度以核验结果为准。尚未完成核验时，提示学生在学习路线确认标记并从核验旁聊开始。"
                + "简历证据仅代表学生自述，掌握程度需要后续核验。INTERVIEW_READY 表示可准备模拟面试，当前还没有开始任何面试。\n"
                + json.writeValueAsString(Map.of("目标方向", active.directionTitle(), "分析版本", active.resumeVersion(),
                        "阶段", report.stage(), "技术证据", report.skills(), "学习路线", report.roadmap()));
    }

    private Summary summary(CareerMapper.Assessment stored) {
        if (stored == null) return null;
        var direction = CareerCatalog.require(stored.directionId());
        var report = stored.report() == null ? null : json.readValue(stored.report(), CareerFit.Report.class);
        return new Summary(Long.toString(stored.id()), stored.resumeVersion(), stored.directionId(), direction.title(),
                stored.status(), report, report != null && !report.usesCurrentAnalysis());
    }

    @PreDestroy
    void destroy() {
        workers.shutdownNow();
    }

    record Option(String id, String title, String description, String roles, List<String> topics) {}
    record Summary(String id, long resumeVersion, String directionId, String directionTitle, CareerMapper.Status status,
                   CareerFit.Report report, boolean needsReanalysis) {}
    record State(String accountId, Long resumeVersion, String resumeName, boolean waitingForText, Summary current,
                 List<Option> directions, String referenceUrl) {}
}
