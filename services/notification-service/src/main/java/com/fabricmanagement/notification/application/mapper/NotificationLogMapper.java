package com.fabricmanagement.notification.application.mapper;

import com.fabricmanagement.notification.api.dto.response.NotificationLogResponse;
import com.fabricmanagement.notification.domain.entity.NotificationLog;
import org.springframework.stereotype.Component;

/**
 * Notification Log Mapper
 * 
 * @author Fabric Management Team
 * @since 1.0
 */
@Component
public class NotificationLogMapper {
    
    /**
     * Map entity to response
     */
    public NotificationLogResponse toResponse(NotificationLog entity) {
        return NotificationLogResponse.builder()
            .id(entity.getId())
            .eventId(entity.getEventId())
            .tenantId(entity.getTenantId())
            .channel(entity.getChannel())
            .template(entity.getTemplate())
            .recipient(entity.getRecipient())
            .subject(entity.getSubject())
            .status(entity.getStatus())
            .errorMessage(entity.getErrorMessage())
            .attempts(entity.getAttempts())
            .sentAt(entity.getSentAt())
            .createdAt(entity.getCreatedAt())
            .build();
    }
}

