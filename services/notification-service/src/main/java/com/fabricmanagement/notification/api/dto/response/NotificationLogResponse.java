package com.fabricmanagement.notification.api.dto.response;

import com.fabricmanagement.notification.domain.valueobject.NotificationChannel;
import com.fabricmanagement.notification.domain.valueobject.NotificationStatus;
import com.fabricmanagement.notification.domain.valueobject.NotificationTemplate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Notification Log Response
 * 
 * @author Fabric Management Team
 * @since 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationLogResponse {
    
    private UUID id;
    private String eventId;
    private UUID tenantId;
    private NotificationChannel channel;
    private NotificationTemplate template;
    private String recipient;
    private String subject;
    private NotificationStatus status;
    private String errorMessage;
    private Integer attempts;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;
}

