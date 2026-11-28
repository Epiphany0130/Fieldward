package com.fieldward.jobs;

import com.fieldward.account.AccountPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/applications")
class ApplicationController {
    private final ApplicationService service;
    ApplicationController(ApplicationService service) { this.service=service; }
    @GetMapping List<ApplicationStore.View> list(@AuthenticationPrincipal AccountPrincipal principal) { return service.list(principal.accountId()); }
    @PutMapping("/{jobId}/favorite") void favorite(@AuthenticationPrincipal AccountPrincipal principal,@PathVariable long jobId,@Valid @RequestBody Favorite input) { service.favorite(principal.accountId(),jobId,input.enabled()); }
    @PutMapping("/{jobId}/progress") ApplicationStore.View progress(@AuthenticationPrincipal AccountPrincipal principal,@PathVariable long jobId,@Valid @RequestBody Progress input) { return service.updateProgress(principal.accountId(),jobId,input.status(),input.requestId()); }
    record Favorite(@NotNull Boolean enabled) {}
    record Progress(@NotBlank String status,@NotBlank String requestId) {}
}
