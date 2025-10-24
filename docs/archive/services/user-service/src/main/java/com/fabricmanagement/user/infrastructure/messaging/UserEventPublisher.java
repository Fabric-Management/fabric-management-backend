package com.fabricmanagement.user.infrastructure.messaging;

import com.fabricmanagement.shared.domain.outbox.OutboxEvent;
import com.fabricmanagement.shared.domain.outbox.OutboxEventStatus;
import com.fabricmanagement.user.domain.event.UserCreatedEvent;
import com.fabricmanagement.user.domain.event.UserDeletedEvent;
import com.fabricmanagement.user.domain.event.UserUpdatedEvent;
import com.fabricmanagement.user.infrastructure.outbox.UserOutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * User Event Publisher
 * 
 * Publishes user domain events using Transactional Outbox Pattern
 * 
 * ✅ ZERO HARDCODED (Manifesto compliance)
 * ✅ OUTBOX PATTERN (Guaranteed delivery)
 * ✅ ZERO DUPLICATION (Uses shared OutboxEvent)
 * 
 * How it works:
 * 1. Write event to outbox table in same transaction as business logic
 * 2. Background publisher (from shared-infrastructure) sends to Kafka
 * 3. Ensures at-least-once delivery guarantee
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventPublisher {
    
    private final UserOutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    
    // ✅ Config-driven topic names (ZERO HARDCODED!)
    @org.springframework.beans.factory.annotation.Value("${kafka.topics.user-created:user.created}")
    private String userCreatedTopic;
    
    @org.springframework.beans.factory.annotation.Value("${kafka.topics.user-updated:user.updated}")
    private String userUpdatedTopic;
    
    @org.springframework.beans.factory.annotation.Value("${kafka.topics.user-deleted:user.deleted}")
    private String userDeletedTopic;
    
    /**
     * Publishes UserCreatedEvent via Outbox Pattern
     * 
     * Event is saved to outbox table, background publisher sends to Kafka
     */
    public void publishUserCreated(UserCreatedEvent event) {
        log.info("📝 Writing UserCreatedEvent to outbox: {}", event.getUserId());
        writeToOutbox(event, userCreatedTopic, "USER", event.getUserId(), UUID.fromString(event.getTenantId()));
    }
    
    /**
     * Publishes UserUpdatedEvent via Outbox Pattern
     */
    public void publishUserUpdated(UserUpdatedEvent event) {
        log.info("📝 Writing UserUpdatedEvent to outbox: {}", event.getUserId());
        writeToOutbox(event, userUpdatedTopic, "USER", event.getUserId(), UUID.fromString(event.getTenantId()));
    }
    
    /**
     * Publishes UserDeletedEvent via Outbox Pattern
     */
    public void publishUserDeleted(UserDeletedEvent event) {
        log.info("📝 Writing UserDeletedEvent to outbox: {}", event.getUserId());
        writeToOutbox(event, userDeletedTopic, "USER", event.getUserId(), UUID.fromString(event.getTenantId()));
    }
    
    /**
     * Generic method to write event to outbox table
     * 
     * This is called in same transaction as business logic
     * Guarantees: Either both succeed or both fail (atomicity)
     * 
     * ✅ Uses shared OutboxEvent (ZERO DUPLICATION)
     */
    private void writeToOutbox(Object event, String topic, String aggregateType, 
                               UUID aggregateId, UUID tenantId) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            
            OutboxEvent outboxEvent = OutboxEvent.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(event.getClass().getSimpleName())
                .eventVersion("1.0")
                .payload(payload)
                .occurredAt(LocalDateTime.now())
                .tenantId(tenantId)
                .status(OutboxEventStatus.NEW)
                .retryCount(0)
                .build();
            
            outboxEventRepository.save(outboxEvent);
            log.info("✅ Event written to outbox: {} (ID: {})", event.getClass().getSimpleName(), outboxEvent.getId());
            
        } catch (JsonProcessingException e) {
            log.error("❌ Failed to serialize event to JSON: {}", event.getClass().getSimpleName(), e);
            throw new RuntimeException("Failed to write event to outbox", e);
        }
    }
}

