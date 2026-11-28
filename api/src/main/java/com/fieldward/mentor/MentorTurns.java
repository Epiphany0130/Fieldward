package com.fieldward.mentor;

import com.fieldward.credits.CreditService;
import com.fieldward.credits.ExpiredCreditCleanup;
import com.fieldward.mentor.MentorTurnMapper.StoredTurn;
import com.fieldward.mentor.MentorTurnMapper.TurnView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class MentorTurns {
    static final long STREAM_TIMEOUT_MS = 60_000L;
    private static final int LEASE_SECONDS = 75;
    private static final int PAGE_SIZE = 30;
    private static final int CONTEXT_BUDGET = 56_000;
    private final MentorTurnMapper mapper;
    private final CreditService credits;
    private final ExpiredCreditCleanup expiredCredits;
    private final MentorMemory memory;
    private final CareerPlans careers;
    private final SkillSessions skills;
    private final LearningMapper learning;
    private final com.fieldward.jobs.ApplicationService applications;

    MentorTurns(MentorTurnMapper mapper, MentorMemory memory, CareerPlans careers, SkillSessions skills, CreditService credits, ExpiredCreditCleanup expiredCredits, LearningMapper learning, com.fieldward.jobs.ApplicationService applications) {
        this.applications = applications;
        this.learning = learning;
        this.expiredCredits = expiredCredits;
        this.credits = credits;
        this.mapper = mapper;
        this.memory = memory;
        this.careers = careers;
        this.skills = skills;
    }

    History history(long accountId, Long side, Long before, String watch, boolean available) {
        long conversation = requireConversation(mapper.findConversation(accountId));
        requireSide(conversation, side);
        var stored = mapper.history(conversation, side, before, PAGE_SIZE + 1);
        var page = stored.subList(0, Math.min(PAGE_SIZE, stored.size()));
        var active = mapper.active(conversation, side);
        var watched = watch == null ? null : mapper.observe(conversation, side, watch);
        String nextBefore = stored.size() > PAGE_SIZE ? Long.toString(page.getLast().id()) : null;
        return new History(Long.toString(accountId), available,
                page.reversed().stream().map(this::view).toList(), nextBefore, view(active), view(watched));
    }

    @Transactional
    public StoredTurn claim(long accountId, Long side, String requestId, String rawMessage, boolean available) {
        expiredCredits.recover(accountId);
        String message = rawMessage.strip();
        if (message.indexOf(0) >= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "消息包含不支持的字符");
        }
        long conversation = requireConversation(mapper.lockConversation(accountId));
        requireSide(conversation, side);
        if (careers.needsOnboarding(conversation)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先选择职业方向并完成简历匹配分析");
        }
        if (skills.testing(conversation, side)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请在技能核验旁聊提交作答或开始带学");
        }
        for (var expired : mapper.expire(conversation, side)) credits.releaseIfPresent(creditRequest(expired));
        var previous = mapper.find(conversation, side, requestId);
        if (previous != null) {
            if (!message.equals(previous.studentMessage())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "该消息编号已用于其他内容");
            }
            if (previous.status() == MentorTurnMapper.Status.COMPLETED) return previous;
        }
        boolean applicationMessage = applications.isApplicationMessage(message);
        if (!available && !applicationMessage) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "当前环境尚未配置 Mentor 模型");
        if (mapper.active(conversation, side) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mentor 正在回复，请稍后再试");
        }
        if (previous != null) {
            credits.releaseIfPresent(creditRequest(previous));
            var retry = mapper.retry(previous.id(), LEASE_SECONDS);
            if (!applicationMessage) credits.reserveAi(accountId, creditRequest(retry), "FREE");
            return retry;
        }
        var turn = mapper.start(conversation, side, requestId, message, LEASE_SECONDS);
        if (!applicationMessage) credits.reserveAi(accountId, creditRequest(turn), "FREE");
        memory.capture(turn);
        return turn;
    }

    private String creditRequest(StoredTurn turn) { return "mentor:" + turn.id() + ":" + turn.attempt(); }

    @Transactional
    public StoredTurn completeApplicationMessage(StoredTurn turn) {
        Long account = mapper.accountForConversation(turn.conversationId());
        if (account == null) return null;
        String reply = applications.updateFromMessage(account, turn.studentMessage(), "mentor-turn:" + turn.id());
        if (reply == null) return null;
        var completed = mapper.complete(turn.id(), turn.attempt(), reply, memory.writeSources(List.of()));
        if (completed == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "对话已过期");
        if (turn.sideChatId() != null && reply.startsWith("已更新")) mapper.openLearning(turn.conversationId(), reply, memory.writeSources(List.of()));
        return completed;
    }

    @Transactional
    public StoredTurn complete(StoredTurn turn, String reply, List<MentorMemory.Source> sources) {
        var completed = mapper.complete(turn.id(), turn.attempt(), reply, memory.writeSources(sources));
        if (completed != null) credits.settleAi(creditRequest(turn));
        return completed;
    }

    @Transactional
    public void interrupt(StoredTurn turn) {
        if (mapper.interrupt(turn.id(), turn.attempt()) == 1) credits.releaseIfPresent(creditRequest(turn));
    }

    Generation context(StoredTurn turn) {
        var prior = new ArrayList<MentorModel.Message>();
        var recent = new HashSet<Long>();
        int remaining = CONTEXT_BUDGET - turn.studentMessage().length();
        for (var completed : mapper.context(turn.conversationId(), turn.sideChatId(), turn.id())) {
            String student = completed.studentMessage();
            int length = completed.mentorReply().length() + (student == null ? 0 : student.length());
            if (length > remaining) break;
            prior.add(new MentorModel.Message("assistant", completed.mentorReply()));
            if (student != null) prior.add(new MentorModel.Message("user", student));
            recent.add(completed.id());
            remaining -= length;
        }
        var messages = new ArrayList<>(prior.reversed());
        var guidance = skills.guidance(turn.conversationId(), turn.sideChatId());
        if (guidance.isBlank()) {
            guidance = careers.guidance(turn.conversationId()) + "\n" + skills.overview(turn.conversationId());
        }
        var topic = learning.context(turn.conversationId(), turn.sideChatId());
        if (topic != null) {
            guidance += "\n当前旁聊是学生主动选择的学习主题：" + topic.title()
                    + "。相关学习目标：" + (topic.objectives() == null ? "按学生需要确定" : topic.objectives())
                    + "。结合学生回答解释概念并给出小练习，一次只问一个问题；不要把学习或自述当作验证通过。";
        }
        if (!guidance.isBlank()) messages.add(new MentorModel.Message("user", guidance));
        var context = memory.retrieve(turn, recent);
        messages.add(new MentorModel.Message("user", context.prompt()));
        messages.add(new MentorModel.Message("user", turn.studentMessage()));
        return new Generation(messages, context.sources());
    }

    String verifyCitations(String reply, List<MentorMemory.Source> sources) {
        return memory.validateReferences(reply, sources);
    }

    TurnView view(StoredTurn turn) {
        if (turn == null) return null;
        return new TurnView(Long.toString(turn.id()), turn.requestId(), turn.studentMessage(), turn.mentorReply(),
                turn.status(), turn.createdAt(), memory.readSources(turn.sources()));
    }

    TurnView interruptedView(StoredTurn turn) {
        return new TurnView(Long.toString(turn.id()), turn.requestId(), turn.studentMessage(), null,
                MentorTurnMapper.Status.INTERRUPTED, turn.createdAt(), List.of());
    }

    List<MentorTurnMapper.Side> sides(long accountId) {
        return mapper.sides(requireConversation(mapper.findConversation(accountId)));
    }

    @Transactional
    public MentorTurnMapper.Side createSide(long accountId, String title) {
        String cleaned = title.strip();
        if (cleaned.isBlank() || cleaned.indexOf(0) >= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入旁聊名称");
        }
        long conversation = requireConversation(mapper.findConversation(accountId));
        mapper.createSide(conversation, cleaned);
        return mapper.sideByTitle(conversation, cleaned);
    }

    private void requireSide(long conversation, Long side) {
        if (side != null && mapper.findSide(conversation, side) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到该旁聊");
        }
    }

    private long requireConversation(Long conversation) {
        if (conversation == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "创建第一份简历后即可开启主对话");
        }
        return conversation;
    }

    record History(String accountId, boolean available, List<TurnView> turns, String nextBefore,
                   TurnView active, TurnView watched) {}

    record Generation(List<MentorModel.Message> messages, List<MentorMemory.Source> sources) {}
}
