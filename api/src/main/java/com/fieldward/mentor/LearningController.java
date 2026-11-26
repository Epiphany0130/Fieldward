package com.fieldward.mentor;

import com.fieldward.account.AccountPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/learning")
class LearningController {
    private final LearningService learning;
    LearningController(LearningService learning) { this.learning = learning; }

    @PostMapping("/topics/{capabilityId}")
    MentorTurnMapper.Side open(@AuthenticationPrincipal AccountPrincipal student, @PathVariable String capabilityId,
                               @Valid @RequestBody Input input) {
        if (!Long.toString(student.accountId()).equals(input.accountId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前登录账号已变更，请重新登录后继续");
        return learning.openTopic(student.accountId(), capabilityId);
    }
    record Input(@NotBlank @Pattern(regexp = "[0-9]{1,19}") String accountId) {}
}
