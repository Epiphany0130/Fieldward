package com.fieldward.mentor;

import com.fieldward.account.AccountPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/career")
class CareerController {
    private final CareerPlans plans;

    CareerController(CareerPlans plans) {
        this.plans = plans;
    }

    @GetMapping
    CareerPlans.State get(@AuthenticationPrincipal AccountPrincipal student) {
        return plans.state(student.accountId());
    }

    @GetMapping("/assessment/{id}")
    CareerPlans.Summary archived(@AuthenticationPrincipal AccountPrincipal student, @PathVariable @Positive long id) {
        var result = plans.archived(student.accountId(), id);
        if (result == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到该分析结果");
        return result;
    }

    @PostMapping("/assessment")
    CareerPlans.Summary analyze(@AuthenticationPrincipal AccountPrincipal student, @Valid @RequestBody Input input) {
        if (!Long.toString(student.accountId()).equals(input.accountId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前登录账号已变更，请重新登录后继续");
        }
        return plans.start(student.accountId(), input.resumeVersion(), input.directionId());
    }

    record Input(@NotBlank @Pattern(regexp = "[0-9]{1,19}") String accountId,
                 @NotNull @Positive Long resumeVersion, @NotBlank @Pattern(regexp = "[a-z][a-z0-9-]{0,39}") String directionId) {}
}
