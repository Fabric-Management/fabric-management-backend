package com.fabricmanagement.notification.application.mapper;

import com.fabricmanagement.notification.api.dto.request.CreateNotificationConfigRequest;
import com.fabricmanagement.notification.api.dto.request.UpdateNotificationConfigRequest;
import com.fabricmanagement.notification.api.dto.response.NotificationConfigResponse;
import com.fabricmanagement.notification.domain.aggregate.NotificationConfig;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Notification Config Mapper
 * 
 * ALL mapping logic centralized here (ZERO mapping in Service).
 * 
 * @author Fabric Management Team
 * @since 1.0
 */
@Component
public class NotificationConfigMapper {
    
    /**
     * Map request to entity (for create)
     */
    public NotificationConfig toEntity(CreateNotificationConfigRequest request, UUID tenantId) {
        return NotificationConfig.builder()
            .tenantId(tenantId)
            .channel(request.getChannel())
            .provider(request.getProvider())
            .isEnabled(request.getIsEnabled())
            .smtpHost(request.getSmtpHost())
            .smtpPort(request.getSmtpPort())
            .smtpUsername(request.getSmtpUsername())
            .smtpPassword(request.getSmtpPassword())
            .fromEmail(request.getFromEmail())
            .fromName(request.getFromName())
            .apiKey(request.getApiKey())
            .fromNumber(request.getFromNumber())
            .priority(request.getPriority() != null ? request.getPriority() : 1)
            .build();
    }
    
    /**
     * Map entity to response (mask sensitive fields)
     */
    public NotificationConfigResponse toResponse(NotificationConfig entity) {
        return NotificationConfigResponse.builder()
            .id(entity.getId())
            .tenantId(entity.getTenantId())
            .channel(entity.getChannel())
            .provider(entity.getProvider())
            .isEnabled(entity.getIsEnabled())
            .smtpHost(entity.getSmtpHost())
            .smtpPort(entity.getSmtpPort())
            .smtpUsername(entity.getSmtpUsername())
            .fromEmail(entity.getFromEmail())
            .fromName(entity.getFromName())
            .fromNumber(entity.getFromNumber())
            .priority(entity.getPriority())
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            // Passwords/API keys masked
            .smtpPassword("***MASKED***")
            .apiKey("***MASKED***")
            .build();
    }
    
    /**
     * Apply partial update to entity (null fields are ignored)
     */
    public void applyUpdate(NotificationConfig entity, UpdateNotificationConfigRequest request) {
        if (request.getIsEnabled() != null) {
            entity.setIsEnabled(request.getIsEnabled());
        }
        if (request.getSmtpHost() != null) {
            entity.setSmtpHost(request.getSmtpHost());
        }
        if (request.getSmtpPort() != null) {
            entity.setSmtpPort(request.getSmtpPort());
        }
        if (request.getSmtpUsername() != null) {
            entity.setSmtpUsername(request.getSmtpUsername());
        }
        if (request.getSmtpPassword() != null) {
            entity.setSmtpPassword(request.getSmtpPassword());
        }
        if (request.getFromEmail() != null) {
            entity.setFromEmail(request.getFromEmail());
        }
        if (request.getFromName() != null) {
            entity.setFromName(request.getFromName());
        }
        if (request.getApiKey() != null) {
            entity.setApiKey(request.getApiKey());
        }
        if (request.getFromNumber() != null) {
            entity.setFromNumber(request.getFromNumber());
        }
        if (request.getPriority() != null) {
            entity.setPriority(request.getPriority());
        }
    }
}

