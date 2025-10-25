package com.fabricmanagement.common.platform.audit.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit Log entity - Comprehensive audit trail.
 *
 * <p>Records ALL critical operations for compliance and security.</p>
 *
 * <h2>What to Audit:</h2>
 * <ul>
 *   <li>User actions (CREATE, UPDATE, DELETE)</li>
 *   <li>Authentication events (LOGIN, LOGOUT, FAILED_LOGIN)</li>
 *   <li>Policy decisions (ALLOW, DENY)</li>
 *   <li>Data changes (before/after values)</li>
 *   <li>Security events (SUSPICIOUS_ACCESS)</li>
 *   <li>Configuration changes</li>
 * </ul>
 */
@Entity
@Table(name = "common_audit_log", schema = "common_audit",
    indexes = {
        @Index(name = "idx_audit_user", columnList = "user_id"),
        @Index(name = "idx_audit_resource", columnList = "resource"),
        @Index(name = "idx_audit_action", columnList = "action"),
        @Index(name = "idx_audit_timestamp", columnList = "timestamp DESC"),
        @Index(name = "idx_audit_severity", columnList = "severity")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog extends BaseEntity {

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "user_uid", length = 100)
    private String userUid;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "resource", nullable = false, length = 100)
    private String resource;

    @Column(name = "resource_id", length = 100)
    private String resourceId;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    @Builder.Default
    private AuditSeverity severity = AuditSeverity.INFO;

    @Column(name = "timestamp", nullable = false)
    @Builder.Default
    private Instant timestamp = Instant.now();

    public static AuditLog create(UUID userId, String userUid, String action, String resource,
                                 String resourceId, String description) {
        return AuditLog.builder()
            .userId(userId)
            .userUid(userUid)
            .action(action)
            .resource(resource)
            .resourceId(resourceId)
            .description(description)
            .severity(AuditSeverity.INFO)
            .timestamp(Instant.now())
            .build();
    }

    public static AuditLog createSecurityEvent(UUID userId, String action, String description, 
                                              String ipAddress, AuditSeverity severity) {
        return AuditLog.builder()
            .userId(userId)
            .action(action)
            .resource("security")
            .description(description)
            .ipAddress(ipAddress)
            .severity(severity)
            .timestamp(Instant.now())
            .build();
    }

    @Override
    protected String getModuleCode() {
        return "AUD";
    }
}

