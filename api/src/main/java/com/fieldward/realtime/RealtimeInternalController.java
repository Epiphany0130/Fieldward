package com.fieldward.realtime;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/internal/realtime")
class RealtimeInternalController {
    private final RealtimeSessionService sessions;
    private final RealtimeAuthorization authorization;
    private final boolean enabled;
    @org.springframework.beans.factory.annotation.Autowired
    RealtimeInternalController(RealtimeSessionService sessions, RealtimeAuthorization authorization) { this(sessions, authorization, false); }
    RealtimeInternalController(RealtimeSessionService sessions, RealtimeAuthorization authorization, boolean enabled) { this.sessions = sessions; this.authorization = authorization; this.enabled = enabled; }
    record Event(@Positive long accountId, @NotNull UUID sessionId, @NotBlank @Size(max=150) String eventId, @PositiveOrZero int segment) {}
    private void authenticate(String credential, String grant, long accountId, UUID sessionId) {
        if (!enabled) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        if (!authorization.serviceCredential(credential) || grant == null || !authorization.verify(grant, accountId, sessionId)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    @GetMapping("/{id}/context")
    RealtimeSessionService.SessionView context(@RequestHeader(value="X-Realtime-Service", required=false) String credential,
            @RequestHeader(value="X-Realtime-Grant", required=false) String grant, @RequestParam long accountId, @PathVariable UUID id) {
        authenticate(credential, grant, accountId, id);
        var session = sessions.get(accountId, id);
        if (!session.status().equals("ACTIVE")) throw new ResponseStatusException(HttpStatus.CONFLICT, "会话未进行");
        return session;
    }
    @PostMapping("/segment")
    RealtimeSessionService.SessionView segment(@RequestHeader(value="X-Realtime-Service", required=false) String credential,
            @RequestHeader(value="X-Realtime-Grant", required=false) String grant, @Valid @RequestBody Event event) {
        authenticate(credential, grant, event.accountId(), event.sessionId());
        return sessions.segment(event.accountId(), event.sessionId(), event.eventId(), event.segment());
    }
}
