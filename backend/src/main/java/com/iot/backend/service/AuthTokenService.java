package com.iot.backend.service;

import com.iot.backend.entity.SysUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * Issues and validates signed stateless API tokens.
 */
@Service
public class AuthTokenService {

    private static final Logger log = LoggerFactory.getLogger(AuthTokenService.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    @Value("${industrial.auth.token-secret:}")
    private String configuredSecret;

    @Value("${industrial.auth.token-ttl-minutes:480}")
    private long tokenTtlMinutes;

    private byte[] secretBytes;

    @PostConstruct
    public void init() {
        if (configuredSecret == null || configuredSecret.trim().isEmpty()) {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            secretBytes = random;
            log.warn("IIOT_AUTH_TOKEN_SECRET is not configured. A temporary token secret was generated; all tokens will become invalid after restart.");
        } else {
            secretBytes = configuredSecret.getBytes(StandardCharsets.UTF_8);
        }
    }

    public IssuedToken issue(SysUser user) {
        long now = System.currentTimeMillis();
        long ttlMillis = Math.max(1, tokenTtlMinutes) * 60L * 1000L;
        long expiresAt = now + ttlMillis;
        String payload = user.getUserId() + ":" + safe(user.getUsername()) + ":" + now + ":" + expiresAt + ":" + UUID.randomUUID();
        String encodedPayload = base64Url(payload.getBytes(StandardCharsets.UTF_8));
        String signature = sign(encodedPayload);
        return new IssuedToken(encodedPayload + "." + signature, expiresAt);
    }

    public Long parseUserId(String token) {
        String[] parts = splitToken(token);
        String encodedPayload = parts[0];
        String expected = sign(encodedPayload);
        if (!constantTimeEquals(expected, parts[1])) {
            throw new IllegalArgumentException("invalid token signature");
        }

        String payload = new String(Base64.getUrlDecoder().decode(encodedPayload), StandardCharsets.UTF_8);
        String[] fields = payload.split(":", 5);
        if (fields.length < 5) {
            throw new IllegalArgumentException("invalid token payload");
        }

        long expiresAt = Long.parseLong(fields[3]);
        if (expiresAt < System.currentTimeMillis()) {
            throw new IllegalArgumentException("token expired");
        }
        return Long.parseLong(fields[0]);
    }

    private String[] splitToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("missing token");
        }
        String[] parts = token.trim().split("\\.");
        if (parts.length != 2) {
            throw new IllegalArgumentException("invalid token");
        }
        return parts;
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretBytes, HMAC_ALGORITHM));
            return base64Url(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("cannot sign token", ex);
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String safe(String value) {
        return value == null ? "" : value.replace(":", "_");
    }

    public static class IssuedToken {
        private final String token;
        private final long expiresAt;

        public IssuedToken(String token, long expiresAt) {
            this.token = token;
            this.expiresAt = expiresAt;
        }

        public String getToken() {
            return token;
        }

        public long getExpiresAt() {
            return expiresAt;
        }
    }
}
