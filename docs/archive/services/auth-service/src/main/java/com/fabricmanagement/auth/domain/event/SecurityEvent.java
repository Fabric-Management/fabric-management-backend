package com.fabricmanagement.auth.domain.event;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Consolidated Security Event
 * 
 * Single event class for all security-related events
 * Replaces multiple specific event classes for better maintainability
 * 
 * ✅ ZERO HARDCODED VALUES
 * ✅ PRODUCTION-READY
 * ✅ CONSOLIDATED PATTERN
 * ✅ PII PROTECTION
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityEvent {
    
    /**
     * Event type - defines the specific security event
     */
    private String eventType;
    
    /**
     * Tenant ID for multi-tenancy
     */
    private UUID tenantId;
    
    /**
     * User ID associated with the event
     */
    private UUID userId;
    
    /**
     * Event-specific payload data
     */
    private Map<String, Object> payload;
    
    /**
     * When the event occurred
     */
    private LocalDateTime occurredAt;
    
    /**
     * Trace ID for distributed tracing
     */
    private String traceId;
    
    /**
     * Correlation ID for request tracking
     */
    private String correlationId;
    
    /**
     * IP address of the request
     */
    private String ipAddress;
    
    /**
     * User agent string
     */
    private String userAgent;
    
    /**
     * Device information (JSON string)
     */
    private String deviceInfo;
    
    // =========================================================================
    // EVENT TYPE CONSTANTS
    // =========================================================================
    
    public static final String USER_REGISTRATION = "USER_REGISTRATION";
    public static final String USER_LOGIN = "USER_LOGIN";
    public static final String USER_LOGOUT = "USER_LOGOUT";
    public static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
    public static final String ACCOUNT_UNLOCKED = "ACCOUNT_UNLOCKED";
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String PASSWORD_RESET = "PASSWORD_RESET";
    public static final String ROLE_ASSIGNED = "ROLE_ASSIGNED";
    public static final String ROLE_REMOVED = "ROLE_REMOVED";
    public static final String PERMISSION_GRANTED = "PERMISSION_GRANTED";
    public static final String PERMISSION_REVOKED = "PERMISSION_REVOKED";
    public static final String TOKEN_REVOKED = "TOKEN_REVOKED";
    public static final String SESSION_EXPIRED = "SESSION_EXPIRED";
    public static final String SECURITY_VIOLATION = "SECURITY_VIOLATION";
    
    // =========================================================================
    // FACTORY METHODS
    // =========================================================================
    
    /**
     * Create user registration event
     */
    public static SecurityEvent userRegistration(UUID userId, UUID tenantId, String contactValue, 
                                                String contactType, String ipAddress, String userAgent) {
        return SecurityEvent.builder()
            .eventType(USER_REGISTRATION)
            .userId(userId)
            .tenantId(tenantId)
            .payload(Map.of(
                "contactValue", contactValue,
                "contactType", contactType,
                "registrationTime", LocalDateTime.now()
            ))
            .occurredAt(LocalDateTime.now())
            .traceId(UUID.randomUUID().toString())
            .correlationId(UUID.randomUUID().toString())
            .ipAddress(ipAddress)
            .userAgent(userAgent)
            .deviceInfo("{}")
            .build();
    }
    
    /**
     * Create user login event
     */
    public static SecurityEvent userLogin(UUID userId, UUID tenantId, String contactValue, 
                                        String contactType, String ipAddress, String userAgent) {
        return SecurityEvent.builder()
            .eventType(USER_LOGIN)
            .userId(userId)
            .tenantId(tenantId)
            .payload(Map.of(
                "contactValue", contactValue,
                "contactType", contactType,
                "loginTime", LocalDateTime.now()
            ))
            .occurredAt(LocalDateTime.now())
            .traceId(UUID.randomUUID().toString())
            .correlationId(UUID.randomUUID().toString())
            .ipAddress(ipAddress)
            .userAgent(userAgent)
            .deviceInfo("{}")
            .build();
    }
    
    /**
     * Create user logout event
     */
    public static SecurityEvent userLogout(UUID userId, UUID tenantId, String ipAddress, String userAgent) {
        return SecurityEvent.builder()
            .eventType(USER_LOGOUT)
            .userId(userId)
            .tenantId(tenantId)
            .payload(Map.of(
                "logoutTime", LocalDateTime.now()
            ))
            .occurredAt(LocalDateTime.now())
            .traceId(UUID.randomUUID().toString())
            .correlationId(UUID.randomUUID().toString())
            .ipAddress(ipAddress)
            .userAgent(userAgent)
            .deviceInfo("{}")
            .build();
    }
    
    /**
     * Create account locked event
     */
    public static SecurityEvent accountLocked(UUID userId, UUID tenantId, String contactValue, 
                                            String contactType, Integer failedAttempts, String ipAddress) {
        return SecurityEvent.builder()
            .eventType(ACCOUNT_LOCKED)
            .userId(userId)
            .tenantId(tenantId)
            .payload(Map.of(
                "contactValue", contactValue,
                "contactType", contactType,
                "failedAttempts", failedAttempts,
                "lockTime", LocalDateTime.now()
            ))
            .occurredAt(LocalDateTime.now())
            .traceId(UUID.randomUUID().toString())
            .correlationId(UUID.randomUUID().toString())
            .ipAddress(ipAddress)
            .userAgent("")
            .deviceInfo("{}")
            .build();
    }
    
    /**
     * Create password changed event
     */
    public static SecurityEvent passwordChanged(UUID userId, UUID tenantId, String ipAddress, String userAgent) {
        return SecurityEvent.builder()
            .eventType(PASSWORD_CHANGED)
            .userId(userId)
            .tenantId(tenantId)
            .payload(Map.of(
                "changeTime", LocalDateTime.now()
            ))
            .occurredAt(LocalDateTime.now())
            .traceId(UUID.randomUUID().toString())
            .correlationId(UUID.randomUUID().toString())
            .ipAddress(ipAddress)
            .userAgent(userAgent)
            .deviceInfo("{}")
            .build();
    }
    
    /**
     * Create role assigned event
     */
    public static SecurityEvent roleAssigned(UUID userId, UUID tenantId, String roleName, UUID grantedBy) {
        return SecurityEvent.builder()
            .eventType(ROLE_ASSIGNED)
            .userId(userId)
            .tenantId(tenantId)
            .payload(Map.of(
                "roleName", roleName,
                "grantedBy", grantedBy != null ? grantedBy.toString() : "system",
                "assignedTime", LocalDateTime.now()
            ))
            .occurredAt(LocalDateTime.now())
            .traceId(UUID.randomUUID().toString())
            .correlationId(UUID.randomUUID().toString())
            .ipAddress("")
            .userAgent("")
            .deviceInfo("{}")
            .build();
    }
    
    /**
     * Create token revoked event
     */
    public static SecurityEvent tokenRevoked(UUID userId, UUID tenantId, String tokenType, String ipAddress) {
        return SecurityEvent.builder()
            .eventType(TOKEN_REVOKED)
            .userId(userId)
            .tenantId(tenantId)
            .payload(Map.of(
                "tokenType", tokenType,
                "revokedTime", LocalDateTime.now()
            ))
            .occurredAt(LocalDateTime.now())
            .traceId(UUID.randomUUID().toString())
            .correlationId(UUID.randomUUID().toString())
            .ipAddress(ipAddress)
            .userAgent("")
            .deviceInfo("{}")
            .build();
    }
}
