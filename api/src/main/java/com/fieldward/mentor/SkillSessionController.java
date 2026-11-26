package com.fieldward.mentor;

import com.fieldward.account.AccountPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/career/assessment/{id}/skills")
class SkillSessionController {
    private final SkillSessions sessions;

    SkillSessionController(SkillSessions sessions) {
        this.sessions = sessions;
    }

    @GetMapping
    ResponseEntity<SkillSessions.View> get(@AuthenticationPrincipal AccountPrincipal student, @PathVariable @Positive long id) {
        var session = sessions.get(student.accountId(), id);
        return session == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(session);
    }

    @PostMapping
    SkillSessions.View start(@AuthenticationPrincipal AccountPrincipal student, @PathVariable @Positive long id,
                             @Valid @RequestBody Start input) {
        verifyAccount(student, input.accountId());
        return sessions.start(student.accountId(), id, input.marks());
    }

    @PostMapping("/step")
    SkillSessions.View step(@AuthenticationPrincipal AccountPrincipal student, @PathVariable @Positive long id,
                            @Valid @RequestBody Step input) {
        verifyAccount(student, input.accountId());
        return sessions.step(student.accountId(), id, input.revision(), input.answer());
    }

    private void verifyAccount(AccountPrincipal student, String accountId) {
        if (!Long.toString(student.accountId()).equals(accountId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前登录账号已变更，请刷新后继续");
        }
    }

    record Start(@NotBlank String accountId, @NotNull @Size(max = 150) Map<String, CareerFit.Mark> marks) {}
    record Step(@NotBlank String accountId, @NotNull @PositiveOrZero Integer revision, @Size(max = 8000) String answer) {}
}
