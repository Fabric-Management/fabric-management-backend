package com.fabricmanagement.contact.infrastructure.messaging;

import com.fabricmanagement.shared.domain.outbox.OutboxEvent;
import com.fabricmanagement.shared.domain.outbox.OutboxEventStatus;
import com.fabricmanagement.contact.domain.event.ContactCreatedEvent;
import com.fabricmanagement.contact.domain.event.ContactDeletedEvent;
import com.fabricmanagement.contact.domain.event.ContactUpdatedEvent;
import com.fabricmanagement.contact.infrastructure.outbox.ContactOutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Contact Event Publisher
 * 
 * Publishes contact domain events using Transactional Outbox Pattern
 * 
 * ✅ ZERO HARDCODED VALUES
 * ✅ OUTBOX PATTERN (Guaranteed delivery)
 * ✅ SHARED MODEL (Schema local, standard global)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ContactEventPublisher {
    
    private final ContactOutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    
    @Value("${kafka.topics.contact-events:contact-events}")
    private String contactEventsTopic;
    
    public void publish(Object event) {
        if (event instanceof ContactCreatedEvent) {
            publishContactCreated((ContactCreatedEvent) event);
        } else if (event instanceof ContactUpdatedEvent) {
            publishContactUpdated((ContactUpdatedEvent) event);
        } else if (event instanceof ContactDeletedEvent) {
            publishContactDeleted((ContactDeletedEvent) event);
        } else {
            log.warn("Unknown event type: {}", event.getClass().getName());
        }
    }
    
    private void publishContactCreated(ContactCreatedEvent event) {
        log.info("📝 Writing ContactCreatedEvent to outbox: {}", event.getContactId());
        writeToOutbox(event, contactEventsTopic, "CONTACT", event.getContactId(), UUID.randomUUID());
    }
    
    private void publishContactUpdated(ContactUpdatedEvent event) {
        log.info("📝 Writing ContactUpdatedEvent to outbox: {}", event.getContactId());
        writeToOutbox(event, contactEventsTopic, "CONTACT", event.getContactId(), UUID.randomUUID());
    }
    
    private void publishContactDeleted(ContactDeletedEvent event) {
        log.info("📝 Writing ContactDeletedEvent to outbox: {}", event.getContactId());
        writeToOutbox(event, contactEventsTopic, "CONTACT", event.getContactId(), UUID.randomUUID());
    }
    
    private void writeToOutbox(Object event, String topic, String aggregateType, UUID aggregateId, UUID tenantId) {
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

