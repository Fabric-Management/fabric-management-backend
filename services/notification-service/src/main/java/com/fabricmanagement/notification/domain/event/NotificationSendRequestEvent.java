package com.fabricmanagement.notification.domain.event;

import com.fabricmanagement.notification.domain.valueobject.NotificationChannel;
import com.fabricmanagement.notification.domain.valueobject.NotificationTemplate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Notification Send Request Event
 * 
 * Kafka event consumed from other services (user/contact/company)
 * requesting notification delivery.
 * 
 * Event Flow:
 * 1. User Service → Kafka → Notification Service
 * 2. Notification Service validates event
 * 3. Fetches tenant config (or uses platform fallback)
 * 4. Sends notification via selected channel
 * 5. Logs result to notification_logs
 * 
 * @author Fabric Management Team
 * @since 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationSendRequestEvent {
    
    /**
     * Unique event ID (for idempotency)
     */
    private String eventId;
    
    /**
     * Event type for routing (e.g. USER_CREATED, PASSWORD_RESET)
     */
    private String eventType;
    
    /**
     * Tenant ID (for config lookup)
     */
    private UUID tenantId;
    
    /**
     * User ID who triggered the event (optional)
     */
    private UUID userId;
    
    /**
     * Preferred notification channel (optional, will fallback)
     */
    private NotificationChannel preferredChannel;
    
    /**
     * Template to use (optional, can be GENERIC with custom body)
     */
    private NotificationTemplate template;
    
    /**
     * Recipient address (email or phone)
     */
    private String recipient;
    
    /**
     * Subject line (for email, optional if template is used)
     */
    private String subject;
    
    /**
     * Message body (optional if template is used)
     */
    private String body;
    
    /**
     * Template variables (e.g. {code: "123456", expiryMinutes: "15"})
     */
    private Map<String, String> variables;
    
    /**
     * Event timestamp
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    
    /**
     * Retry count (for tracking)
     */
    @Builder.Default
    private Integer retryCount = 0;
}

