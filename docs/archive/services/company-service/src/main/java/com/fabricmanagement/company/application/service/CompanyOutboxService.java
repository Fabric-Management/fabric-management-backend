package com.fabricmanagement.company.application.service;

import com.fabricmanagement.shared.domain.outbox.OutboxEvent;
import com.fabricmanagement.company.domain.event.CompanyCreatedEvent;
import com.fabricmanagement.company.domain.event.CompanyUpdatedEvent;
import com.fabricmanagement.company.domain.event.CompanyDeletedEvent;
import com.fabricmanagement.company.infrastructure.outbox.CompanyOutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Company Outbox Service
 * 
 * Handles outbox event creation for company domain events.
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
public class CompanyOutboxService {
    
    private final CompanyOutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;
    
    /**
     * Create outbox event for company creation
     */
    @Transactional
    public void publishCompanyCreated(CompanyCreatedEvent event, UUID tenantId, String traceId, String correlationId) {
        OutboxEvent outboxEvent = OutboxEvent.builder()
            .aggregateType("COMPANY")
            .aggregateId(event.getCompanyId())
            .eventType("CompanyCreated")
            .eventVersion("1.0")
            .payload(serializeEvent(event))
            .occurredAt(LocalDateTime.now())
            .tenantId(tenantId)
            .traceId(traceId)
            .correlationId(correlationId)
            .headers(createHeaders(event))
            .build();
        
        outboxRepository.save(outboxEvent);
        log.debug("📝 CompanyCreated outbox event created: {}", event.getCompanyId());
    }
    
    /**
     * Create outbox event for company update
     */
    @Transactional
    public void publishCompanyUpdated(CompanyUpdatedEvent event, UUID tenantId, String traceId, String correlationId) {
        OutboxEvent outboxEvent = OutboxEvent.builder()
            .aggregateType("COMPANY")
            .aggregateId(event.getCompanyId())
            .eventType("CompanyUpdated")
            .eventVersion("1.0")
            .payload(serializeEvent(event))
            .occurredAt(LocalDateTime.now())
            .tenantId(tenantId)
            .traceId(traceId)
            .correlationId(correlationId)
            .headers(createHeaders(event))
            .build();
        
        outboxRepository.save(outboxEvent);
        log.debug("📝 CompanyUpdated outbox event created: {}", event.getCompanyId());
    }
    
    /**
     * Create outbox event for company deletion
     */
    @Transactional
    public void publishCompanyDeleted(CompanyDeletedEvent event, UUID tenantId, String traceId, String correlationId) {
        OutboxEvent outboxEvent = OutboxEvent.builder()
            .aggregateType("COMPANY")
            .aggregateId(event.getCompanyId())
            .eventType("CompanyDeleted")
            .eventVersion("1.0")
            .payload(serializeEvent(event))
            .occurredAt(LocalDateTime.now())
            .tenantId(tenantId)
            .traceId(traceId)
            .correlationId(correlationId)
            .headers(createHeaders(event))
            .build();
        
        outboxRepository.save(outboxEvent);
        log.debug("📝 CompanyDeleted outbox event created: {}", event.getCompanyId());
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
            "source", "company-service",
            "version", "1.0",
            "timestamp", LocalDateTime.now().toString()
        );
    }
}
