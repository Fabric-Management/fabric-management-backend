package com.fabricmanagement.fiber.application.service;

import com.fabricmanagement.shared.domain.outbox.OutboxEvent;
import com.fabricmanagement.fiber.domain.event.FiberDefinedEvent;
import com.fabricmanagement.fiber.domain.event.FiberUpdatedEvent;
import com.fabricmanagement.fiber.domain.event.FiberDeactivatedEvent;
import com.fabricmanagement.fiber.infrastructure.outbox.FiberOutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Fiber Outbox Service
 * 
 * Handles outbox event creation for fiber domain events.
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
public class FiberOutboxService {
    
    private final FiberOutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;
    
    /**
     * Create outbox event for fiber definition
     */
    @Transactional
    public void publishFiberDefined(FiberDefinedEvent event, UUID tenantId, String traceId, String correlationId) {
        OutboxEvent outboxEvent = OutboxEvent.builder()
            .aggregateType("FIBER")
            .aggregateId(event.getFiberId())
            .eventType("FiberDefined")
            .eventVersion("1.0")
            .payload(serializeEvent(event))
            .occurredAt(LocalDateTime.now())
            .tenantId(tenantId)
            .traceId(traceId)
            .correlationId(correlationId)
            .headers(createHeaders(event))
            .build();
        
        outboxRepository.save(outboxEvent);
        log.debug("📝 FiberDefined outbox event created: {}", event.getFiberId());
    }
    
    /**
     * Create outbox event for fiber update
     */
    @Transactional
    public void publishFiberUpdated(FiberUpdatedEvent event, UUID tenantId, String traceId, String correlationId) {
        OutboxEvent outboxEvent = OutboxEvent.builder()
            .aggregateType("FIBER")
            .aggregateId(event.getFiberId())
            .eventType("FiberUpdated")
            .eventVersion("1.0")
            .payload(serializeEvent(event))
            .occurredAt(LocalDateTime.now())
            .tenantId(tenantId)
            .traceId(traceId)
            .correlationId(correlationId)
            .headers(createHeaders(event))
            .build();
        
        outboxRepository.save(outboxEvent);
        log.debug("📝 FiberUpdated outbox event created: {}", event.getFiberId());
    }
    
    /**
     * Create outbox event for fiber deactivation
     */
    @Transactional
    public void publishFiberDeactivated(FiberDeactivatedEvent event, UUID tenantId, String traceId, String correlationId) {
        OutboxEvent outboxEvent = OutboxEvent.builder()
            .aggregateType("FIBER")
            .aggregateId(event.getFiberId())
            .eventType("FiberDeactivated")
            .eventVersion("1.0")
            .payload(serializeEvent(event))
            .occurredAt(LocalDateTime.now())
            .tenantId(tenantId)
            .traceId(traceId)
            .correlationId(correlationId)
            .headers(createHeaders(event))
            .build();
        
        outboxRepository.save(outboxEvent);
        log.debug("📝 FiberDeactivated outbox event created: {}", event.getFiberId());
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
            "source", "fiber-service",
            "version", "1.0",
            "timestamp", LocalDateTime.now().toString()
        );
    }
}
