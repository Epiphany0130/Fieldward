package com.fieldward.mentor;

import com.fieldward.account.AccountPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/mentor/turns")
class MentorChatController {
    private final MentorTurns turns;
    private final MentorModel model;
    private final MentorStream stream;

    MentorChatController(MentorTurns turns, MentorModel model, MentorStream stream) {
        this.turns = turns;
        this.model = model;
        this.stream = stream;
    }

    @GetMapping
    MentorTurns.History history(@AuthenticationPrincipal AccountPrincipal student,
                               @RequestParam(required = false) @Positive Long before,
                               @RequestParam(required = false) @Positive Long side,
                               @RequestParam(required = false) UUID watch) {
        return turns.history(student.accountId(), side, before, watch == null ? null : watch.toString(), model.available());
    }

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    ResponseEntity<SseEmitter> send(@AuthenticationPrincipal AccountPrincipal student,
                                    @RequestParam(required = false) @Positive Long side,
                                    @RequestBody @Valid StudentMessage message) throws IOException {
        if (!Long.toString(student.accountId()).equals(message.accountId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前登录账号已变更，请重新登录后再发送");
        }
        var turn = turns.claim(student.accountId(), side, message.requestId().toString(), message.content(), model.available());
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .header("X-Accel-Buffering", "no")
                .body(stream.open(turn));
    }

    record StudentMessage(@NotNull @Pattern(regexp = "[0-9]{1,19}") String accountId,
                          @NotNull UUID requestId, @NotBlank @Size(max = 4000) String content) {}
}
