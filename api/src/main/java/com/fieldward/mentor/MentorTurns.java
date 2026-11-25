package com.fieldward.mentor;

import com.fieldward.mentor.MentorTurnMapper.StoredTurn;
import com.fieldward.mentor.MentorTurnMapper.TurnView;
import java.util.ArrayList;
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
    private static final int CONTEXT_BUDGET = 12_000;
    private final MentorTurnMapper mapper;

    MentorTurns(MentorTurnMapper mapper) {
        this.mapper = mapper;
    }

    History history(long accountId, Long before, String watch, boolean available) {
        long conversation = requireConversation(mapper.findConversation(accountId));
        var stored = mapper.history(conversation, before, PAGE_SIZE + 1);
        var page = stored.subList(0, Math.min(PAGE_SIZE, stored.size()));
        var active = mapper.active(conversation);
        var watched = watch == null ? null : mapper.observe(conversation, watch);
        String nextBefore = stored.size() > PAGE_SIZE ? Long.toString(page.getLast().id()) : null;
        return new History(Long.toString(accountId), available,
                page.reversed().stream().map(StoredTurn::view).toList(),
                nextBefore, active == null ? null : active.view(), watched == null ? null : watched.view());
    }

    @Transactional
    public StoredTurn claim(long accountId, String requestId, String rawMessage, boolean available) {
        String message = rawMessage.strip();
        if (message.indexOf(0) >= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "消息包含不支持的字符");
        }
        long conversation = requireConversation(mapper.lockConversation(accountId));
        mapper.expire(conversation);
        var previous = mapper.find(conversation, requestId);
        if (previous != null) {
            if (!previous.studentMessage().equals(message)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "该消息编号已用于其他内容");
            }
            if (previous.status() == MentorTurnMapper.Status.COMPLETED) return previous;
        }
        if (!available) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "当前环境尚未配置 Mentor 模型");
        if (mapper.active(conversation) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mentor 正在回复，请稍后再试");
        }
        return previous == null ? mapper.start(conversation, requestId, message, LEASE_SECONDS)
                : mapper.retry(previous.id(), LEASE_SECONDS);
    }

    StoredTurn complete(StoredTurn turn, String reply) {
        return mapper.complete(turn.id(), turn.attempt(), reply);
    }

    void interrupt(StoredTurn turn) {
        mapper.interrupt(turn.id(), turn.attempt());
    }

    List<MentorModel.Message> context(StoredTurn turn) {
        var prior = new ArrayList<MentorModel.Message>();
        int remaining = CONTEXT_BUDGET - turn.studentMessage().length();
        for (var completed : mapper.context(turn.conversationId(), turn.id())) {
            int length = completed.mentorReply().length() + completed.studentMessage().length();
            if (length > remaining) break;
            prior.add(new MentorModel.Message("assistant", completed.mentorReply()));
            prior.add(new MentorModel.Message("user", completed.studentMessage()));
            remaining -= length;
        }
        var messages = new ArrayList<>(prior.reversed());
        messages.add(new MentorModel.Message("user", turn.studentMessage()));
        return messages;
    }

    private long requireConversation(Long conversation) {
        if (conversation == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "创建第一份简历后即可开启主对话");
        }
        return conversation;
    }

    record History(String accountId, boolean available, List<TurnView> turns, String nextBefore,
                   TurnView active, TurnView watched) {}
}
