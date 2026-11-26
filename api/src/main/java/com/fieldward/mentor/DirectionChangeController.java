package com.fieldward.mentor;

import com.fieldward.account.AccountPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/career/direction-changes")
class DirectionChangeController {
    private final DirectionChangeService changes;
    DirectionChangeController(DirectionChangeService changes) { this.changes = changes; }
    record Request(@NotBlank String directionId) {}
    @PostMapping
    DirectionChangeService.Proposal propose(@AuthenticationPrincipal AccountPrincipal account, @Valid @RequestBody Request request) {
        return changes.propose(account.accountId(), request.directionId());
    }
    @PostMapping("/{id}/confirm")
    DirectionChangeService.DirectionView confirm(@AuthenticationPrincipal AccountPrincipal account, @PathVariable UUID id) {
        return changes.confirmDirectionChange(account.accountId(), id);
    }
}
