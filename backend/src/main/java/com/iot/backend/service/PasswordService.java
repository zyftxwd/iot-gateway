package com.iot.backend.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Centralizes password hashing and legacy plain-text password compatibility.
 */
@Service
public class PasswordService {

    private static final String BCRYPT_PREFIX = "{bcrypt}";
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    public String hash(String rawPassword) {
        if (rawPassword == null || rawPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("password is required");
        }
        return BCRYPT_PREFIX + encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null || storedPassword.isEmpty()) {
            return false;
        }
        if (isHashed(storedPassword)) {
            return encoder.matches(rawPassword, normalizeHash(storedPassword));
        }
        return storedPassword.equals(rawPassword);
    }

    public boolean needsRehash(String storedPassword) {
        return storedPassword != null && !isHashed(storedPassword);
    }

    private boolean isHashed(String storedPassword) {
        return storedPassword.startsWith(BCRYPT_PREFIX)
                || storedPassword.startsWith("$2a$")
                || storedPassword.startsWith("$2b$")
                || storedPassword.startsWith("$2y$");
    }

    private String normalizeHash(String storedPassword) {
        if (storedPassword.startsWith(BCRYPT_PREFIX)) {
            return storedPassword.substring(BCRYPT_PREFIX.length());
        }
        return storedPassword;
    }
}
