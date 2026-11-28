package com.fieldward.mentor;

import com.fieldward.credits.CreditService;
import com.fieldward.credits.ExpiredCreditCleanup;
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
    private final CareerDirections directions;
    private final org.springframework.jdbc.core.JdbcTemplate db;
    private final CreditService credits;
    private final ExpiredCreditCleanup expiredCredits;
    private final MentorConversationMapper conversations;
    private final MentorResumeMapper resumes;
    private final MentorTurnMapper turns;
    private final CareerFit fit;
    private final JsonMapper json;
    private final boolean ocrEnabled;

    CareerWrites(CareerMapper careers, MentorConversationMapper conversations, MentorResumeMapper resumes,
                 MentorTurnMapper turns, CareerFit fit, JsonMapper json,
                 @Value("${fieldward.mentor.ocr.enabled}") boolean ocrEnabled, CreditService credits, ExpiredCreditCleanup expiredCredits, CareerDirections directions, org.springframework.jdbc.core.JdbcTemplate db) {
        this.db = db;
        this.directions = directions;
        this.expiredCredits = expiredCredits;
        this.credits = credits;
        this.careers = careers;
        this.conversations = conversations;
        this.resumes = resumes;
        this.turns = turns;
        this.fit = fit;
        this.json = json;
        this.ocrEnabled = ocrEnabled;
    }

    @Transactional
    public CareerPlans.ExplorationState beginExploration(long accountId) {
        Long conversation = turns.lockConversation(accountId);
        if (conversation == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "请先创建或上传正式简历");
        if (careers.needsOnboarding(conversation)) {
            careers.finishOnboarding(accountId);
            turns.openLearning(conversation, "你好！我们可以先从通用知识开始，也可以一起探索适合你的方向。你最近对什么技术或问题感兴趣？", "[]");
        }
        return new CareerPlans.ExplorationState(Long.toString(conversation));
    }

    @Transactional
    public Claim claim(long accountId, String direction, long version) {
        expiredCredits.recover(accountId);
        if (careers.lockAccount(accountId) == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        var workspace = conversations.findWorkspace(accountId);
        if (workspace == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "请先创建或上传正式简历");
        if (workspace.latestResumeVersion() != version) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "简历已有新版本，请刷新后再分析");
        }
        var previous = careers.current(accountId);
        if (previous != null && !previous.directionId().equals(direction)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先比较并确认方向切换");
        }

        // The administrator locks this same row when creating a route revision. Keep the
        // selected definition and the assessment claim in one transaction.
        if (db.queryForList("SELECT id FROM career_directions WHERE id=? FOR SHARE", direction).isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择一个有效的职业方向");
        var selection = directions.require(direction);
        int routeVersion = selection.version();
        var document = resumes.find(Long.parseLong(workspace.conversationId()), version);
        var source = fit.input(document);
        var status = waitingForText(document.readable(), document.ocrStatus())
                ? CareerMapper.Status.WAITING_TEXT : CareerMapper.Status.ANALYZING;
        String fingerprint = digest(source, status, routeVersion);
        var ticket = careers.claim(accountId, version, direction, fingerprint, status.name());
        var selected = ticket == null ? careers.existing(accountId, version, direction, fingerprint) : ticket;
        careers.activate(accountId, selected.id());
        boolean start = ticket != null && status == CareerMapper.Status.ANALYZING;
        if (start && !source.text().isBlank()) {
            if (selected.attempt() > 1) credits.releaseIfPresent(creditRequest(selected.id(), selected.attempt() - 1));
            credits.reserveAi(accountId, creditRequest(selected.id(), selected.attempt()), "DIAGNOSIS");
        }
        var earlier = start ? careers.latestReady(accountId, version, direction) : null;
        Long earlierReport = earlier != null
                && json.readTree(earlier.report()).path("analysisVersion").asInt(0) < CareerFit.ANALYSIS_VERSION
                ? earlier.id() : null;
        return new Claim(selected, source, start, earlierReport, selection);
    }

    boolean waitingForText(boolean readable, String ocrStatus) {
        return ocrEnabled && !readable && ocrStatus != null && OCR_PENDING.contains(ocrStatus);
    }

    @Transactional
    public void finish(Claim claim, CareerFit.Report report) {
        var assessment = claim.assessment();
        if (careers.lockAccount(assessment.accountId()) == null) return;
        // Chat claims lock the conversation before the credit account; use the same order.
        if (claim.earlierReport() != null) turns.lockConversation(assessment.accountId());
        if (careers.settle(assessment, "READY", json.writeValueAsString(report)) == 0) return;
        if (!claim.input().text().isBlank()) credits.settleAi(creditRequest(assessment.id(), assessment.attempt()));
        credits.protect(assessment.accountId(), "DIAGNOSIS", 0);

        var active = careers.current(assessment.accountId());
        var workspace = conversations.findWorkspace(assessment.accountId());
        if (active == null || active.id() != assessment.id() || workspace == null
                || workspace.latestResumeVersion() != assessment.resumeVersion()) return;
        careers.finishOnboarding(assessment.accountId());
        if (claim.earlierReport() != null) {
            turns.reviseUnansweredOpening(Long.parseLong(workspace.conversationId()),
                    planPath(claim.earlierReport()), "我们先在学习路线核对技能，再逐项练习。",
                    json.writeValueAsString(List.of(new MentorMemory.Source("S1", "本次职业匹配与学习路线",
                            planPath(assessment.id()), "确认技术栈后开始核验"))));
        }
    }

    private String planPath(long assessment) {
        return "/workspace/career/plan/" + assessment;
    }

    @Transactional
    public void fail(CareerMapper.Assessment assessment) {
        if (careers.settle(assessment, "FAILED", null) == 1)
            credits.releaseIfPresent(creditRequest(assessment.id(), assessment.attempt()));
    }

    private String creditRequest(long id, int attempt) { return "career:" + id + ":" + attempt; }

    private String digest(CareerFit.Input input, CareerMapper.Status status, int routeVersion) {
        try {
            String evidence = CareerFit.ANALYSIS_VERSION + "\n" + status + "\n" + input.scan() + "\n" + input.text() + (routeVersion > 1 ? "\nroute:" + routeVersion : "");
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(evidence.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    record Claim(CareerMapper.Assessment assessment, CareerFit.Input input, boolean start, Long earlierReport, CareerDirections.Selection selection) {
        Claim(CareerMapper.Assessment assessment, CareerFit.Input input, boolean start, Long earlierReport) { this(assessment, input, start, earlierReport, null); }
    }
}
