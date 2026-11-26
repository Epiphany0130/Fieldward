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
    private static final String RUBRIC_VERSION = "1";
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
        return new Claim(selected, source, ticket != null && status == CareerMapper.Status.ANALYZING);
    }

    boolean waitingForText(boolean readable, String ocrStatus) {
        return ocrEnabled && !readable && ocrStatus != null && OCR_PENDING.contains(ocrStatus);
    }

    @Transactional
    public void finish(CareerMapper.Assessment assessment, CareerCatalog.Direction direction, CareerFit.Report report) {
        if (careers.lockAccount(assessment.accountId()) == null) return;
        if (careers.settle(assessment, "READY", json.writeValueAsString(report)) == 0) return;

        var active = careers.current(assessment.accountId());
        var workspace = conversations.findWorkspace(assessment.accountId());
        if (active == null || active.id() != assessment.id() || workspace == null
                || workspace.latestResumeVersion() != assessment.resumeVersion()) return;
        careers.finishOnboarding(assessment.accountId());
        if (report.stage() != CareerFit.Stage.LEARNING) return;

        var first = report.roadmap().getFirst();
        var skill = direction.skills().stream().filter(item -> item.id().equals(first.skillId())).findFirst().orElseThrow();
        String opening = "我们先从 **" + first.title() + "** 开始。\n\n" + skill.question();
        var sources = List.of(new MentorMemory.Source("S1", "本次职业匹配与学习计划", "/workspace/career/plan/" + assessment.id(),
                first.objective() + "\n" + first.task()));
        turns.openLearning(Long.parseLong(workspace.conversationId()), opening, json.writeValueAsString(sources));
    }

    void fail(CareerMapper.Assessment assessment) {
        careers.settle(assessment, "FAILED", null);
    }

    private String digest(CareerFit.Input input, CareerMapper.Status status) {
        try {
            String evidence = RUBRIC_VERSION + "\n" + status + "\n" + input.scan() + "\n" + input.text();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(evidence.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    record Claim(CareerMapper.Assessment assessment, CareerFit.Input input, boolean start) {}
}
