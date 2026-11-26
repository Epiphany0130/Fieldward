package com.fieldward.realtime;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class RealtimeAuthorization {
    private final byte[] secret;
    private final Clock clock;
    @Autowired
    public RealtimeAuthorization(@Value("${fieldward.realtime.secret:}") String secret) { this(secret, Clock.systemUTC()); }
    RealtimeAuthorization(String secret, Clock clock) { this.secret = secret.getBytes(StandardCharsets.UTF_8); this.clock = clock; }
    private void configured() { if (secret.length < 32) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "实时服务未配置"); }
    public void requireConfigured() { configured(); }
    private byte[] sign(String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException e) { throw new IllegalStateException(e); }
    }
    public String issue(long accountId, UUID id) {
        configured();
        String body = accountId + ":" + id + ":" + (clock.instant().getEpochSecond() + 300);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(body.getBytes(StandardCharsets.UTF_8)) + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(body));
    }
    public boolean verify(String token, long accountId, UUID id) {
        configured();
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 2) return false;
            String body = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            byte[] signature = Base64.getUrlDecoder().decode(parts[1]);
            if (!MessageDigest.isEqual(sign(body), signature)) return false;
            String[] claims = body.split(":", -1);
            return claims.length == 3 && Long.parseLong(claims[0]) == accountId && UUID.fromString(claims[1]).equals(id) && Long.parseLong(claims[2]) > clock.instant().getEpochSecond();
        } catch (RuntimeException e) { return false; }
    }
    public boolean serviceCredential(String value) {
        configured();
        return value != null && MessageDigest.isEqual(secret, value.getBytes(StandardCharsets.UTF_8));
    }
}
