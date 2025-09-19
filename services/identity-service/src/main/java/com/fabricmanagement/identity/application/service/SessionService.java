package com.fabricmanagement.identity.application.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing temporary session tokens.
 * In production, use Redis or similar cache.
 */
@Service
public class SessionService {

    private final Map<String, SessionData> sessions = new ConcurrentHashMap<>();

    public String createSession(UUID userId, String purpose, long ttlMinutes) {
        String token = UUID.randomUUID().toString();
        SessionData data = new SessionData(
            userId,
            purpose,
            System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(ttlMinutes)
        );
        sessions.put(token, data);
        return token;
    }

    public SessionData getSession(String token) {
        SessionData data = sessions.get(token);
        if (data != null && data.expiresAt > System.currentTimeMillis()) {
            return data;
        }
        sessions.remove(token);
        return null;
    }

    public void invalidateSession(String token) {
        sessions.remove(token);
    }

    public void storeVerificationCode(String contact, String code, long ttlMinutes) {
        String key = "verify:" + contact;
        SessionData data = new SessionData(
            null,
            code,
            System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(ttlMinutes)
        );
        sessions.put(key, data);
    }

    public boolean verifyCode(String contact, String code) {
        String key = "verify:" + contact;
        SessionData data = sessions.get(key);
        if (data != null && data.purpose.equals(code) && data.expiresAt > System.currentTimeMillis()) {
            sessions.remove(key);
            return true;
        }
        return false;
    }

    public static class SessionData {
        public final UUID userId;
        public final String purpose;
        public final long expiresAt;

        public SessionData(UUID userId, String purpose, long expiresAt) {
            this.userId = userId;
            this.purpose = purpose;
            this.expiresAt = expiresAt;
        }
    }
}