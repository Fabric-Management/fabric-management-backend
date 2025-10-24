package com.fabricmanagement.user.infrastructure.audit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Security Audit Logger
 * 
 * Logs security-related events for compliance and monitoring.
 * These logs can be ingested by SIEM systems for analysis.
 * 
 * Future enhancements:
 * - Send to dedicated audit log storage (e.g., Elasticsearch)
 * - Structured logging with JSON format
 * - Integration with monitoring/alerting systems
 */
@Component
@Slf4j
public class SecurityAuditLogger {

    private static final String AUDIT_LOG_PREFIX = "[SECURITY_AUDIT]";

    /**
     * Logs successful login
     */
    public void logSuccessfulLogin(String contactValue, String userId, String tenantId) {
        Map<String, Object> event = new HashMap<>();
        event.put("event", "LOGIN_SUCCESS");
        event.put("contactValue", maskContact(contactValue));
        event.put("userId", userId);
        event.put("tenantId", tenantId);
        event.put("timestamp", LocalDateTime.now());
        
        log.info("{} {}", AUDIT_LOG_PREFIX, formatEvent(event));
    }

    /**
     * Logs failed login attempt
     */
    public void logFailedLogin(String contactValue, String reason) {
        Map<String, Object> event = new HashMap<>();
        event.put("event", "LOGIN_FAILED");
        event.put("contactValue", maskContact(contactValue));
        event.put("reason", reason);
        event.put("timestamp", LocalDateTime.now());
        
        log.warn("{} {}", AUDIT_LOG_PREFIX, formatEvent(event));
    }

    /**
     * Logs account lockout
     */
    public void logAccountLockout(String contactValue, int attempts, int lockoutMinutes) {
        Map<String, Object> event = new HashMap<>();
        event.put("event", "ACCOUNT_LOCKED");
        event.put("contactValue", maskContact(contactValue));
        event.put("attempts", attempts);
        event.put("lockoutDuration", lockoutMinutes);
        event.put("timestamp", LocalDateTime.now());
        
        log.warn("{} {}", AUDIT_LOG_PREFIX, formatEvent(event));
    }

    /**
     * Logs password setup
     */
    public void logPasswordSetup(String contactValue, String userId) {
        Map<String, Object> event = new HashMap<>();
        event.put("event", "PASSWORD_SETUP");
        event.put("contactValue", maskContact(contactValue));
        event.put("userId", userId);
        event.put("timestamp", LocalDateTime.now());
        
        log.info("{} {}", AUDIT_LOG_PREFIX, formatEvent(event));
    }

    /**
     * Logs password change
     */
    public void logPasswordChange(String userId, String reason) {
        Map<String, Object> event = new HashMap<>();
        event.put("event", "PASSWORD_CHANGED");
        event.put("userId", userId);
        event.put("reason", reason);
        event.put("timestamp", LocalDateTime.now());
        
        log.info("{} {}", AUDIT_LOG_PREFIX, formatEvent(event));
    }

    /**
     * Logs suspicious activity
     */
    public void logSuspiciousActivity(String contactValue, String activityType, String details) {
        Map<String, Object> event = new HashMap<>();
        event.put("event", "SUSPICIOUS_ACTIVITY");
        event.put("contactValue", maskContact(contactValue));
        event.put("activityType", activityType);
        event.put("details", details);
        event.put("timestamp", LocalDateTime.now());
        
        log.warn("{} {}", AUDIT_LOG_PREFIX, formatEvent(event));
    }

    /**
     * Logs unauthorized access attempt
     */
    public void logUnauthorizedAccess(String path, String userId, String reason) {
        Map<String, Object> event = new HashMap<>();
        event.put("event", "UNAUTHORIZED_ACCESS");
        event.put("path", path);
        event.put("userId", userId != null ? userId : "anonymous");
        event.put("reason", reason);
        event.put("timestamp", LocalDateTime.now());
        
        log.warn("{} {}", AUDIT_LOG_PREFIX, formatEvent(event));
    }

    /**
     * Masks contact value for privacy (shows first 3 chars + ***)
     * Example: user@example.com → use***
     */
    private String maskContact(String contact) {
        if (contact == null || contact.length() <= 3) {
            return "***";
        }
        return contact.substring(0, 3) + "***";
    }

    /**
     * Formats event as string
     */
    private String formatEvent(Map<String, Object> event) {
        StringBuilder sb = new StringBuilder();
        event.forEach((key, value) -> {
            sb.append(key).append("=").append(value).append(" ");
        });
        return sb.toString().trim();
    }
}
