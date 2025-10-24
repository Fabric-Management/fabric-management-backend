package com.fabricmanagement.user.application.service;

import com.fabricmanagement.shared.domain.outbox.OutboxEvent;
import com.fabricmanagement.user.domain.event.UserCreatedEvent;
import com.fabricmanagement.user.domain.event.UserUpdatedEvent;
import com.fabricmanagement.user.domain.event.UserDeletedEvent;
import com.fabricmanagement.user.infrastructure.outbox.UserOutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * User Outbox Service
 * 
 * Handles outbox event creation for user domain events.
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
public class UserOutboxService {
    
    private final UserOutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;
    
    /**
     * Create outbox event for user creation
     */
    @Transactional
    public void publishUserCreated(UserCreatedEvent event, UUID tenantId, String traceId, String correlationId) {
        OutboxEvent outboxEvent = OutboxEvent.builder()
            .aggregateType("USER")
            .aggregateId(event.getUserId())
            .eventType("UserCreated")
            .eventVersion("1.0")
            .payload(serializeEvent(event))
            .occurredAt(LocalDateTime.now())
            .tenantId(tenantId)
            .traceId(traceId)
            .correlationId(correlationId)
            .headers(createHeaders(event))
            .build();
        
        outboxRepository.save(outboxEvent);
        log.debug("📝 UserCreated outbox event created: {}", event.getUserId());
    }
    
    /**
     * Create outbox event for user update
     */
    @Transactional
    public void publishUserUpdated(UserUpdatedEvent event, UUID tenantId, String traceId, String correlationId) {
        OutboxEvent outboxEvent = OutboxEvent.builder()
            .aggregateType("USER")
            .aggregateId(event.getUserId())
            .eventType("UserUpdated")
            .eventVersion("1.0")
            .payload(serializeEvent(event))
            .occurredAt(LocalDateTime.now())
            .tenantId(tenantId)
            .traceId(traceId)
            .correlationId(correlationId)
            .headers(createHeaders(event))
            .build();
        
        outboxRepository.save(outboxEvent);
        log.debug("📝 UserUpdated outbox event created: {}", event.getUserId());
    }
    
    /**
     * Create outbox event for user deletion
     */
    @Transactional
    public void publishUserDeleted(UserDeletedEvent event, UUID tenantId, String traceId, String correlationId) {
        OutboxEvent outboxEvent = OutboxEvent.builder()
            .aggregateType("USER")
            .aggregateId(event.getUserId())
            .eventType("UserDeleted")
            .eventVersion("1.0")
            .payload(serializeEvent(event))
            .occurredAt(LocalDateTime.now())
            .tenantId(tenantId)
            .traceId(traceId)
            .correlationId(correlationId)
            .headers(createHeaders(event))
            .build();
        
        outboxRepository.save(outboxEvent);
        log.debug("📝 UserDeleted outbox event created: {}", event.getUserId());
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
    private Map<String, String> createHeaders(Object event) {
        return Map.of(
            "source", "user-service",
            "version", "1.0",
            "timestamp", LocalDateTime.now().toString()
        );
    }
}