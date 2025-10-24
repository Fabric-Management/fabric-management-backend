package com.fabricmanagement.company.infrastructure.messaging;

import com.fabricmanagement.shared.domain.outbox.OutboxEvent;
import com.fabricmanagement.shared.domain.outbox.OutboxEventStatus;
import com.fabricmanagement.company.domain.event.CompanyCreatedEvent;
import com.fabricmanagement.company.domain.event.CompanyDeletedEvent;
import com.fabricmanagement.company.domain.event.CompanyUpdatedEvent;
import com.fabricmanagement.company.infrastructure.outbox.CompanyOutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Company Event Publisher
 * 
 * Publishes company domain events using Transactional Outbox Pattern
 * 
 * ✅ ZERO HARDCODED VALUES
 * ✅ OUTBOX PATTERN (Guaranteed delivery)
 * ✅ SHARED MODEL (Schema local, standard global)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CompanyEventPublisher {
    
    private final CompanyOutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    
    @org.springframework.beans.factory.annotation.Value("${kafka.topics.company-events:company-events}")
    private String companyEventsTopic;
    
    public void publishCompanyCreated(CompanyCreatedEvent event) {
        log.info("📝 Writing CompanyCreatedEvent to outbox: {}", event.getCompanyId());
        writeToOutbox(event, companyEventsTopic, "COMPANY", event.getCompanyId(), UUID.fromString(event.getTenantId()));
    }
    
    public void publishCompanyUpdated(CompanyUpdatedEvent event) {
        log.info("📝 Writing CompanyUpdatedEvent to outbox: {}", event.getCompanyId());
        writeToOutbox(event, companyEventsTopic, "COMPANY", event.getCompanyId(), UUID.fromString(event.getTenantId()));
    }
    
    public void publishCompanyDeleted(CompanyDeletedEvent event) {
        log.info("📝 Writing CompanyDeletedEvent to outbox: {}", event.getCompanyId());
        writeToOutbox(event, companyEventsTopic, "COMPANY", event.getCompanyId(), UUID.fromString(event.getTenantId()));
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
            log.error("❌ Failed to serialize event to JSON", e);
            throw new RuntimeException("Failed to write event to outbox", e);
        }
    }
}

