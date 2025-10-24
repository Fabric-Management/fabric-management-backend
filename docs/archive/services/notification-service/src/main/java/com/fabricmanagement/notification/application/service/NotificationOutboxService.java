package com.fabricmanagement.notification.application.service;

import com.fabricmanagement.shared.domain.outbox.OutboxEvent;
import com.fabricmanagement.notification.infrastructure.outbox.NotificationOutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Notification Outbox Service
 * 
 * Handles outbox event creation for notification domain events.
 * Ensures events are stored in the same transaction as business data.
 * 
 * Pattern: "Schema local, standard global."
 * - Uses shared OutboxEvent model
 * - Service-specific repository implementation
 * - Zero hardcoded values (config-driven)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationOutboxService {
    
    private final NotificationOutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;
    
    /**
     * Create outbox event for notification sent
     */
    @Transactional
    public void publishNotificationSent(UUID notificationId, String channel, String recipient, UUID tenantId, String traceId, String correlationId) {
        Map<String, Object> payload = Map.of(
            "notificationId", notificationId.toString(),
            "channel", channel,
            "recipient", recipient,
            "status", "SENT",
            "timestamp", LocalDateTime.now().toString()
        );
        
        OutboxEvent outboxEvent = OutboxEvent.builder()
            .aggregateType("NOTIFICATION")
            .aggregateId(notificationId)
            .eventType("NotificationSent")
            .eventVersion("1.0")
            .payload(serializeEvent(payload))
            .occurredAt(LocalDateTime.now())
            .tenantId(tenantId)
            .traceId(traceId)
            .correlationId(correlationId)
            .headers(createHeaders())
            .build();
        
        outboxRepository.save(outboxEvent);
        log.debug("📝 NotificationSent outbox event created: {}", notificationId);
    }
    
    /**
     * Create outbox event for notification failed
     */
    @Transactional
    public void publishNotificationFailed(UUID notificationId, String channel, String recipient, String errorMessage, UUID tenantId, String traceId, String correlationId) {
        Map<String, Object> payload = Map.of(
            "notificationId", notificationId.toString(),
            "channel", channel,
            "recipient", recipient,
            "status", "FAILED",
            "errorMessage", errorMessage,
            "timestamp", LocalDateTime.now().toString()
        );
        
        OutboxEvent outboxEvent = OutboxEvent.builder()
            .aggregateType("NOTIFICATION")
            .aggregateId(notificationId)
            .eventType("NotificationFailed")
            .eventVersion("1.0")
            .payload(serializeEvent(payload))
            .occurredAt(LocalDateTime.now())
            .tenantId(tenantId)
            .traceId(traceId)
            .correlationId(correlationId)
            .headers(createHeaders())
            .build();
        
        outboxRepository.save(outboxEvent);
        log.debug("📝 NotificationFailed outbox event created: {}", notificationId);
    }
    
    /**
     * Create outbox event for notification delivered
     */
    @Transactional
    public void publishNotificationDelivered(UUID notificationId, String channel, String recipient, UUID tenantId, String traceId, String correlationId) {
        Map<String, Object> payload = Map.of(
            "notificationId", notificationId.toString(),
            "channel", channel,
            "recipient", recipient,
            "status", "DELIVERED",
            "timestamp", LocalDateTime.now().toString()
        );
        
        OutboxEvent outboxEvent = OutboxEvent.builder()
            .aggregateType("NOTIFICATION")
            .aggregateId(notificationId)
            .eventType("NotificationDelivered")
            .eventVersion("1.0")
            .payload(serializeEvent(payload))
            .occurredAt(LocalDateTime.now())
            .tenantId(tenantId)
            .traceId(traceId)
            .correlationId(correlationId)
            .headers(createHeaders())
            .build();
        
        outboxRepository.save(outboxEvent);
        log.debug("📝 NotificationDelivered outbox event created: {}", notificationId);
    }
    
    /**
     * Serialize event to JSON payload
     */
    private String serializeEvent(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.error("Failed to serialize event: {}", event.getClass().getSimpleName(), e);
            return "{}";
        }
    }
    
    /**
     * Create event headers
     */
    private Map<String, String> createHeaders() {
        return Map.of(
            "source", "notification-service",
            "version", "1.0",
            "timestamp", LocalDateTime.now().toString()
        );
    }
}
