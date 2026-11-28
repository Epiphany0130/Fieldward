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
    private final org.springframework.jdbc.core.JdbcTemplate db;
    private final org.springframework.security.crypto.password.PasswordEncoder passwords;

    AdminController(AccountMapper accounts, CreditService credits, org.springframework.jdbc.core.JdbcTemplate db, org.springframework.security.crypto.password.PasswordEncoder passwords) {
        this.accounts = accounts;
        this.credits = credits;
        this.db = db;
        this.passwords = passwords;
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

    @PostMapping("/{id}/password")
    void resetPassword(@PathVariable @Min(1) long id, @RequestBody @Valid PasswordInput input) {
        if (input.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码最多 72 字节");
        if (db.update("UPDATE accounts SET password_hash=?,session_version=session_version+1 WHERE id=?", passwords.encode(input.password()), id) == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到该账号");
    }

    @PatchMapping("/{id}/role") @org.springframework.transaction.annotation.Transactional
    AccountView role(@AuthenticationPrincipal AccountPrincipal operator, @PathVariable @Min(1) long id, @RequestBody @Valid RoleInput input) {
        guardAdmin(operator, id);
        db.update("UPDATE accounts SET role=?,session_version=session_version+1 WHERE id=?", input.role().name(), id);
        return AccountView.from(accounts.findById(id));
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{id}") @org.springframework.transaction.annotation.Transactional
    void delete(@AuthenticationPrincipal AccountPrincipal operator, @PathVariable @Min(1) long id) {
        guardAdmin(operator, id);
        db.update("DELETE FROM accounts WHERE id=?", id);
    }

    private void guardAdmin(AccountPrincipal operator, long id) {
        db.queryForList("SELECT id FROM accounts WHERE role='ADMIN' ORDER BY id FOR UPDATE");
        Account account = accounts.findById(id);
        if (account == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到该账号");
        if (operator.accountId() == id || (account.role() == Account.Role.ADMIN && db.queryForObject("SELECT count(*) FROM accounts WHERE role='ADMIN' AND enabled", Long.class) <= 1)) throw new ResponseStatusException(HttpStatus.CONFLICT, "无法修改当前或最后的管理员账号");
    }
    record PasswordInput(@NotBlank @Size(min=8, max=72) String password) {}
    record RoleInput(@NotNull Account.Role role) {}

    record CreditInput(@NotNull Long points, @NotBlank @Size(max = 200) String reason,
                       @NotBlank @Size(max = 200) String requestId) {}

    record StatusInput(@NotNull Boolean enabled) {}

    record UserPage(List<AccountView> users, int page, boolean hasNext) {}
}
