package com.fieldward.realtime;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RealtimeAuthorizationTest {
    @Test void grantIsBoundAndExpires() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        var issuer = new RealtimeAuthorization("01234567890123456789012345678901", Clock.fixed(now, ZoneOffset.UTC));
        UUID id = UUID.randomUUID();
        String token = issuer.issue(42, id);
        assertThat(issuer.verify(token, 42, id)).isTrue();
        assertThat(issuer.verify(token, 43, id)).isFalse();
        assertThat(issuer.verify(token, 42, UUID.randomUUID())).isFalse();
        assertThat(issuer.verify(token + "x", 42, id)).isFalse();
        var later = new RealtimeAuthorization("01234567890123456789012345678901", Clock.fixed(now.plusSeconds(301), ZoneOffset.UTC));
        assertThat(later.verify(token, 42, id)).isFalse();
    }
}
