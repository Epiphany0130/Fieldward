package com.fieldward.realtime;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RealtimeInternalControllerTest {
    @Test void requiresServiceCredentialAndBoundGrant() {
        var service = mock(RealtimeSessionService.class);
        String secret = "01234567890123456789012345678901";
        var auth = new RealtimeAuthorization(secret, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        var controller = new RealtimeInternalController(service, auth, true);
        UUID id = UUID.randomUUID();
        var event = new RealtimeInternalController.Event(42, id, "event", 0);
        String grant = auth.issue(42, id);
        assertThatThrownBy(() -> controller.segment("wrong", grant, event)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.segment(secret, auth.issue(43, id), event)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(service);
        when(service.get(42, id)).thenReturn(new RealtimeSessionService.SessionView(id, "DIAGNOSIS", "PHONE", "PAUSED", 1, "saved"));
        assertThatThrownBy(() -> controller.context(secret, grant, 42, id)).isInstanceOf(ResponseStatusException.class);
        when(service.get(42, id)).thenReturn(new RealtimeSessionService.SessionView(id, "DIAGNOSIS", "PHONE", "ACTIVE", 1, "saved"));
        assertThat(controller.context(secret, grant, 42, id).summary()).isEqualTo("saved");
        controller.segment(secret, grant, event);
        verify(service).segment(42, id, "event", 0);
    }
}
