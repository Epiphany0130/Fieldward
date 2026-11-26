package com.fieldward.mentor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@Service
class CareerWrites {
    private static final Set<String> OCR_PENDING = Set.of("PENDING", "PROCESSING", "RETRY");
    private final CareerMapper careers;
    private final MentorConversationMapper conversations;
    private final MentorResumeMapper resumes;
    private final MentorTurnMapper turns;
    private final CareerFit fit;
    private final JsonMapper json;
    private final boolean ocrEnabled;

    CareerWrites(CareerMapper careers, MentorConversationMapper conversations, MentorResumeMapper resumes,
                 MentorTurnMapper turns, CareerFit fit, JsonMapper json,
                 @Value("${fieldward.mentor.ocr.enabled}") boolean ocrEnabled) {
        this.careers = careers;
        this.conversations = conversations;
        this.resumes = resumes;
        this.turns = turns;
        this.fit = fit;
        this.json = json;
        this.ocrEnabled = ocrEnabled;
    }

    @Transactional
    public Claim claim(long accountId, String direction, long version) {
        if (careers.lockAccount(accountId) == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        var workspace = conversations.findWorkspace(accountId);
        if (workspace == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "请先创建或上传正式简历");
        if (workspace.latestResumeVersion() != version) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "简历已有新版本，请刷新后再分析");
        }
        var previous = careers.current(accountId);
        if (previous != null && previous.status() == CareerMapper.Status.ANALYZING && previous.resumeVersion() == version
                && !previous.directionId().equals(direction)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "正在分析当前方向，稍后可以更换");
        }

        var document = resumes.find(Long.parseLong(workspace.conversationId()), version);
        var source = fit.input(document);
        var status = waitingForText(document.readable(), document.ocrStatus())
                ? CareerMapper.Status.WAITING_TEXT : CareerMapper.Status.ANALYZING;
        String fingerprint = digest(source, status);
        var ticket = careers.claim(accountId, version, direction, fingerprint, status.name());
        var selected = ticket == null ? careers.existing(accountId, version, direction, fingerprint) : ticket;
        careers.activate(accountId, selected.id());
        boolean start = ticket != null && status == CareerMapper.Status.ANALYZING;
        var earlier = start ? careers.latestReady(accountId, version, direction) : null;
        Long earlierReport = earlier != null
                && json.readTree(earlier.report()).path("analysisVersion").asInt(0) < CareerFit.ANALYSIS_VERSION
                ? earlier.id() : null;
        return new Claim(selected, source, start, earlierReport);
    }

    boolean waitingForText(boolean readable, String ocrStatus) {
        return ocrEnabled && !readable && ocrStatus != null && OCR_PENDING.contains(ocrStatus);
    }

    @Transactional
    public void finish(Claim claim, CareerCatalog.Direction direction, CareerFit.Report report) {
        var assessment = claim.assessment();
        if (careers.lockAccount(assessment.accountId()) == null) return;
        if (careers.settle(assessment, "READY", json.writeValueAsString(report)) == 0) return;

        var active = careers.current(assessment.accountId());
        var workspace = conversations.findWorkspace(assessment.accountId());
        if (active == null || active.id() != assessment.id() || workspace == null
                || workspace.latestResumeVersion() != assessment.resumeVersion()) return;
        careers.finishOnboarding(assessment.accountId());
        long conversation = Long.parseLong(workspace.conversationId());
        if (claim.earlierReport() != null) {
            turns.lockConversation(assessment.accountId());
            var updated = opening(assessment.id(), direction, report);
            if (turns.reviseUnansweredOpening(conversation, planPath(claim.earlierReport()), updated.text(), updated.sources()) != 0
                    || !turns.history(conversation, null, null, 1).isEmpty()) return;
        }
        if (report.stage() == CareerFit.Stage.LEARNING) {
            var opening = opening(assessment.id(), direction, report);
            turns.openLearning(conversation, opening.text(), opening.sources());
        }
    }

    private Opening opening(long assessment, CareerCatalog.Direction direction, CareerFit.Report report) {
        if (report.stage() == CareerFit.Stage.INTERVIEW_READY) {
            return new Opening("根据简历证据和已掌握的前置技能，你已达到面试准备标准。可以在工作台查看准备页。",
                    json.writeValueAsString(List.of(new MentorMemory.Source("S1", "本次职业匹配与面试准备",
                            "/workspace/career/interview/" + assessment, "匹配度 " + report.matchPercent() + "%"))));
        }
        var first = report.roadmap().getFirst();
        var skill = direction.skills().stream().filter(item -> item.id().equals(first.skillId())).findFirst().orElseThrow();
        String message = "我们先从 **" + first.title() + "** 开始。\n\n" + skill.question();
        var sources = List.of(new MentorMemory.Source("S1", "本次职业匹配与学习计划", planPath(assessment),
                first.objective() + "\n" + first.task()));
        return new Opening(message, json.writeValueAsString(sources));
    }

    private String planPath(long assessment) {
        return "/workspace/career/plan/" + assessment;
    }

    void fail(CareerMapper.Assessment assessment) {
        careers.settle(assessment, "FAILED", null);
    }

    private String digest(CareerFit.Input input, CareerMapper.Status status) {
        try {
            String evidence = CareerFit.ANALYSIS_VERSION + "\n" + status + "\n" + input.scan() + "\n" + input.text();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(evidence.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    record Claim(CareerMapper.Assessment assessment, CareerFit.Input input, boolean start, Long earlierReport) {}
    private record Opening(String text, String sources) {}
}
