package com.fieldward.account;

import com.fieldward.credits.CreditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/users")
class AdminController {
    private final AccountMapper accounts;
    private final CreditService credits;

    AdminController(AccountMapper accounts, CreditService credits) {
        this.accounts = accounts;
        this.credits = credits;
    }

    @GetMapping
    UserPage list(@RequestParam(defaultValue = "") @Size(max = 254) String q,
                  @RequestParam(defaultValue = "1") @Min(1) int page,
                  @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        List<AccountView> users = accounts.list(q.strip(), size + 1, (page - 1L) * size);
        boolean hasNext = users.size() > size;
        return new UserPage(hasNext ? users.subList(0, size) : users, page, hasNext);
    }

    @PatchMapping("/{id}/status")
    AccountView updateStatus(@PathVariable @Min(1) long id, @RequestBody @Valid StatusInput input) {
        Account account = accounts.setStudentEnabled(id, input.enabled());
        if (account == null) {
            if (accounts.findById(id) == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到该账号");
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "管理中心仅能修改学生账号状态");
        }
        return AccountView.from(account);
    }

    @PostMapping("/{id}/credits")
    void adjustCredits(@AuthenticationPrincipal AccountPrincipal operator, @PathVariable @Min(1) long id,
                       @RequestBody @Valid CreditInput input) {
        credits.adjust(id, operator.accountId(), input.points(), input.reason(), input.requestId());
    }

    record CreditInput(@NotNull Long points, @NotBlank @Size(max = 200) String reason,
                       @NotBlank @Size(max = 200) String requestId) {}

    record StatusInput(@NotNull Boolean enabled) {}

    record UserPage(List<AccountView> users, int page, boolean hasNext) {}
}
