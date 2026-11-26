package com.fieldward.realtime;

import com.fieldward.account.AccountPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/realtime")
class RealtimeController {
    private final RealtimeSessionService sessions;
    private final RealtimeAuthorization authorization;
    private final boolean enabled;
    RealtimeController(RealtimeSessionService sessions, RealtimeAuthorization authorization) {
        this.sessions = sessions; this.authorization = authorization; this.enabled = false;
    }
    private void requireEnabled() {
        if (!enabled) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "实时对话尚未开放");
        authorization.requireConfigured();
    }
    record Start(@NotBlank String purpose, @NotBlank String mode, @NotBlank @Size(max=150) String requestId) {}
    record Checkpoint(@PositiveOrZero int revision, @NotNull @Size(max=600) String summary) {}
    record Finish(@NotBlank @Size(max=150) String requestId) {}
    record Grant(String token) {}
    @PostMapping
    RealtimeSessionService.SessionView start(@AuthenticationPrincipal AccountPrincipal account, @Valid @RequestBody Start input) {
        requireEnabled();
        return sessions.start(account.accountId(), input.purpose(), input.mode(), input.requestId());
    }
    @GetMapping("/{id}")
    RealtimeSessionService.SessionView get(@AuthenticationPrincipal AccountPrincipal account, @PathVariable UUID id) { return sessions.get(account.accountId(), id); }
    @PostMapping("/{id}/checkpoint")
    RealtimeSessionService.SessionView checkpoint(@AuthenticationPrincipal AccountPrincipal account, @PathVariable UUID id, @Valid @RequestBody Checkpoint input) { requireEnabled(); return sessions.checkpoint(account.accountId(), id, input.revision(), input.summary()); }
    @PostMapping("/{id}/resume")
    RealtimeSessionService.SessionView resume(@AuthenticationPrincipal AccountPrincipal account, @PathVariable UUID id) { requireEnabled(); return sessions.resume(account.accountId(), id); }
    @PostMapping("/{id}/finish")
    RealtimeSessionService.SessionView finish(@AuthenticationPrincipal AccountPrincipal account, @PathVariable UUID id, @Valid @RequestBody Finish input) { requireEnabled(); return sessions.finish(account.accountId(), id, input.requestId()); }
    @PostMapping("/{id}/grant")
    Grant grant(@AuthenticationPrincipal AccountPrincipal account, @PathVariable UUID id) {
        requireEnabled();
        var session = sessions.get(account.accountId(), id);
        if (!session.status().equals("ACTIVE")) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "会话未进行");
        return new Grant(authorization.issue(account.accountId(), id));
    }
}
