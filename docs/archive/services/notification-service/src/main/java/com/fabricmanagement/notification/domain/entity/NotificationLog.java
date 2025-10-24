package com.fabricmanagement.notification.domain.entity;

import com.fabricmanagement.notification.domain.valueobject.NotificationChannel;
import com.fabricmanagement.notification.domain.valueobject.NotificationStatus;
import com.fabricmanagement.notification.domain.valueobject.NotificationTemplate;
import com.fabricmanagement.shared.domain.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Notification Log Entity
 * 
 * Tracks all notification delivery attempts.
 * Used for monitoring, debugging, and analytics.
 * 
 * Features:
 * - Delivery status tracking
 * - Retry mechanism support
 * - Error logging
 * - Performance metrics
 * 
 * @author Fabric Management Team
 * @since 1.0
 */
@Entity
@Table(
    name = "notification_logs",
    indexes = {
        @Index(name = "idx_tenant_status", columnList = "tenant_id, status"),
        @Index(name = "idx_recipient", columnList = "recipient"),
        @Index(name = "idx_created_at", columnList = "created_at"),
        @Index(name = "idx_event_id", columnList = "event_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@ToString(callSuper = true)
public class NotificationLog extends BaseEntity {
    
    /**
     * Tenant ID (for multi-tenancy isolation)
     */
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    
    /**
     * Kafka event ID (for idempotency)
     */
    @Column(name = "event_id", nullable = false)
    private String eventId;
    
    /**
     * Notification channel used
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 50)
    private NotificationChannel channel;
    
    /**
     * Template used (optional)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "template", length = 50)
    private NotificationTemplate template;
    
    /**
     * Recipient address (email/phone)
     */
    @Column(name = "recipient", nullable = false, length = 255)
    private String recipient;
    
    /**
     * Subject line (for email)
     */
    @Column(name = "subject", length = 500)
    private String subject;
    
    /**
     * Message body (truncated for logs)
     */
    @Column(name = "body", columnDefinition = "TEXT")
    private String body;
    
    /**
     * Delivery status
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private NotificationStatus status = NotificationStatus.PENDING;
    
    /**
     * Error message (if failed)
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    /**
     * Number of delivery attempts
     */
    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private Integer attempts = 0;
    
    /**
     * Timestamp when successfully sent
     */
    @Column(name = "sent_at")
    private LocalDateTime sentAt;
    
    /**
     * User ID who triggered the notification (optional)
     */
    @Column(name = "triggered_by")
    private UUID triggeredBy;
}

