package com.fieldward.mentor;

import com.fieldward.mentor.MentorTurnMapper.Status;
import com.fieldward.mentor.MentorTurnMapper.StoredTurn;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
class MentorStream {
    private static final Logger log = LoggerFactory.getLogger(MentorStream.class);
    private static final int HEARTBEAT_SECONDS = 3;
    private static final int MAX_REPLY_LENGTH = 12_000;
    private final MentorTurns turns;
    private final MentorModel model;
    private final java.util.concurrent.ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final java.util.concurrent.ScheduledExecutorService clock = Executors.newSingleThreadScheduledExecutor();

    MentorStream(MentorTurns turns, MentorModel model) {
        this.turns = turns;
        this.model = model;
    }

    SseEmitter open(StoredTurn turn) throws IOException {
        var emitter = new SseEmitter(MentorTurns.STREAM_TIMEOUT_MS);
        send(emitter, "turn", turns.view(turn));
        if (turn.status() == Status.COMPLETED) {
            send(emitter, "done", turns.view(turn));
            emitter.complete();
            return emitter;
        }

        var finished = new AtomicBoolean();
        var heartbeat = new AtomicReference<ScheduledFuture<?>>();
        var task = new FutureTask<Void>(() -> {
            try {
                generate(emitter, turn, finished);
            } finally {
                stopHeartbeat(heartbeat);
                emitter.complete();
            }
            return null;
        });
        Runnable disconnect = () -> {
            stopHeartbeat(heartbeat);
            if (finished.compareAndSet(false, true)) {
                task.cancel(true);
                turns.interrupt(turn);
            }
        };
        emitter.onCompletion(disconnect);
        emitter.onError(error -> disconnect.run());
        emitter.onTimeout(() -> {
            disconnect.run();
            emitter.complete();
        });
        try {
            heartbeat.set(clock.scheduleWithFixedDelay(() -> {
                if (finished.get()) return;
                try {
                    emitter.send(SseEmitter.event().comment("keepalive"));
                } catch (IOException | IllegalStateException error) {
                    disconnect.run();
                    emitter.complete();
                }
            }, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS));
            workers.execute(task);
        } catch (RejectedExecutionException error) {
            disconnect.run();
            emitter.complete();
        }
        return emitter;
    }

    private void generate(SseEmitter emitter, StoredTurn turn, AtomicBoolean finished) {
        var reply = new StringBuilder();
        try {
            var context = turns.context(turn);
            model.stream(context.messages(), text -> {
                if (finished.get()) throw new IOException("Client disconnected");
                if (reply.length() + text.length() > MAX_REPLY_LENGTH || text.indexOf(0) >= 0) {
                    throw new IOException("Mentor response exceeded limits");
                }
                reply.append(text);
                send(emitter, "delta", new Delta(text));
            });
            if (finished.get()) return;
            String answer = turns.verifyCitations(reply.toString(), context.sources());
            if (answer.isEmpty()) throw new IOException("Mentor response was empty");
            var persisted = turns.complete(turn, answer, context.sources());
            if (persisted == null) throw new IOException("Mentor generation lease expired");
            if (finished.compareAndSet(false, true)) send(emitter, "done", turns.view(persisted));
        } catch (Exception error) {
            if (finished.compareAndSet(false, true)) {
                log.warn("Mentor turn {} interrupted ({})", turn.id(), error.getClass().getSimpleName());
                turns.interrupt(turn);
                try {
                    send(emitter, "interrupted", turns.interruptedView(turn));
                } catch (IOException | IllegalStateException socketClosed) {
                    // The persisted state is available on the next history request.
                }
            }
        }
    }

    private static void stopHeartbeat(AtomicReference<ScheduledFuture<?>> heartbeat) {
        var future = heartbeat.getAndSet(null);
        if (future != null) future.cancel(false);
    }

    private static void send(SseEmitter emitter, String event, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
    }

    @PreDestroy
    void destroy() {
        clock.shutdownNow();
        workers.shutdownNow();
    }

    record Delta(String text) {}
}
