package com.fieldward.mentor;

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
    private final MentorMemory memory;

    MentorTurns(MentorTurnMapper mapper, MentorMemory memory) {
        this.mapper = mapper;
        this.memory = memory;
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
        String message = rawMessage.strip();
        if (message.indexOf(0) >= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "消息包含不支持的字符");
        }
        long conversation = requireConversation(mapper.lockConversation(accountId));
        requireSide(conversation, side);
        mapper.expire(conversation, side);
        var previous = mapper.find(conversation, side, requestId);
        if (previous != null) {
            if (!previous.studentMessage().equals(message)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "该消息编号已用于其他内容");
            }
            if (previous.status() == MentorTurnMapper.Status.COMPLETED) return previous;
        }
        if (!available) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "当前环境尚未配置 Mentor 模型");
        if (mapper.active(conversation, side) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mentor 正在回复，请稍后再试");
        }
        if (previous != null) return mapper.retry(previous.id(), LEASE_SECONDS);
        var turn = mapper.start(conversation, side, requestId, message, LEASE_SECONDS);
        memory.capture(turn);
        return turn;
    }

    StoredTurn complete(StoredTurn turn, String reply, List<MentorMemory.Source> sources) {
        return mapper.complete(turn.id(), turn.attempt(), reply, memory.writeSources(sources));
    }

    void interrupt(StoredTurn turn) {
        mapper.interrupt(turn.id(), turn.attempt());
    }

    Generation context(StoredTurn turn) {
        var prior = new ArrayList<MentorModel.Message>();
        var recent = new HashSet<Long>();
        int remaining = CONTEXT_BUDGET - turn.studentMessage().length();
        for (var completed : mapper.context(turn.conversationId(), turn.sideChatId(), turn.id())) {
            int length = completed.mentorReply().length() + completed.studentMessage().length();
            if (length > remaining) break;
            prior.add(new MentorModel.Message("assistant", completed.mentorReply()));
            prior.add(new MentorModel.Message("user", completed.studentMessage()));
            recent.add(completed.id());
            remaining -= length;
        }
        var messages = new ArrayList<>(prior.reversed());
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
