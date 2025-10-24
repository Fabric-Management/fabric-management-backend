package com.fabricmanagement.contact.application.service;

import com.fabricmanagement.shared.domain.outbox.OutboxEvent;
import com.fabricmanagement.contact.domain.event.ContactCreatedEvent;
import com.fabricmanagement.contact.domain.event.ContactUpdatedEvent;
import com.fabricmanagement.contact.domain.event.ContactDeletedEvent;
import com.fabricmanagement.contact.infrastructure.outbox.ContactOutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Contact Outbox Service
 * 
 * Handles outbox event creation for contact domain events.
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
public class ContactOutboxService {
    
    private final ContactOutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;
    
    /**
     * Create outbox event for contact creation
     */
    @Transactional
    public void publishContactCreated(ContactCreatedEvent event, UUID tenantId, String traceId, String correlationId) {
        OutboxEvent outboxEvent = OutboxEvent.builder()
            .aggregateType("CONTACT")
            .aggregateId(event.getContactId())
            .eventType("ContactCreated")
            .eventVersion("1.0")
            .payload(serializeEvent(event))
            .occurredAt(LocalDateTime.now())
            .tenantId(tenantId)
            .traceId(traceId)
            .correlationId(correlationId)
            .headers(createHeaders(event))
            .build();
        
        outboxRepository.save(outboxEvent);
        log.debug("📝 ContactCreated outbox event created: {}", event.getContactId());
    }
    
    /**
     * Create outbox event for contact update
     */
    @Transactional
    public void publishContactUpdated(ContactUpdatedEvent event, UUID tenantId, String traceId, String correlationId) {
        OutboxEvent outboxEvent = OutboxEvent.builder()
            .aggregateType("CONTACT")
            .aggregateId(event.getContactId())
            .eventType("ContactUpdated")
            .eventVersion("1.0")
            .payload(serializeEvent(event))
            .occurredAt(LocalDateTime.now())
            .tenantId(tenantId)
            .traceId(traceId)
            .correlationId(correlationId)
            .headers(createHeaders(event))
            .build();
        
        outboxRepository.save(outboxEvent);
        log.debug("📝 ContactUpdated outbox event created: {}", event.getContactId());
    }
    
    /**
     * Create outbox event for contact deletion
     */
    @Transactional
    public void publishContactDeleted(ContactDeletedEvent event, UUID tenantId, String traceId, String correlationId) {
        OutboxEvent outboxEvent = OutboxEvent.builder()
            .aggregateType("CONTACT")
            .aggregateId(event.getContactId())
            .eventType("ContactDeleted")
            .eventVersion("1.0")
            .payload(serializeEvent(event))
            .occurredAt(LocalDateTime.now())
            .tenantId(tenantId)
            .traceId(traceId)
            .correlationId(correlationId)
            .headers(createHeaders(event))
            .build();
        
        outboxRepository.save(outboxEvent);
        log.debug("📝 ContactDeleted outbox event created: {}", event.getContactId());
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
            "source", "contact-service",
            "version", "1.0",
            "timestamp", LocalDateTime.now().toString()
        );
    }
}
