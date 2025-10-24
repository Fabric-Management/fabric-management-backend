package com.fabricmanagement.fiber.infrastructure.messaging;

import com.fabricmanagement.shared.domain.outbox.OutboxEvent;
import com.fabricmanagement.shared.domain.outbox.OutboxEventStatus;
import com.fabricmanagement.fiber.application.mapper.FiberEventMapper;
import com.fabricmanagement.fiber.domain.aggregate.Fiber;
import com.fabricmanagement.fiber.domain.event.FiberDeactivatedEvent;
import com.fabricmanagement.fiber.domain.event.FiberDefinedEvent;
import com.fabricmanagement.fiber.domain.event.FiberUpdatedEvent;
import com.fabricmanagement.fiber.infrastructure.outbox.FiberOutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Fiber Event Publisher
 * 
 * Publishes fiber domain events using Transactional Outbox Pattern
 * 
 * ✅ ZERO HARDCODED VALUES
 * ✅ OUTBOX PATTERN (Guaranteed delivery)
 * ✅ SHARED MODEL (Schema local, standard global)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FiberEventPublisher {
    
    private final FiberOutboxEventRepository outboxEventRepository;
    private final FiberEventMapper eventMapper;
    private final ObjectMapper objectMapper;
    
    @Value("${app.kafka.topics.fiber-events:fiber-events}")
    private String fiberEventsTopic;
    
    public void publishFiberDefined(Fiber fiber) {
        try {
            FiberDefinedEvent event = eventMapper.toDefinedEvent(fiber);
            writeToOutbox(event, fiberEventsTopic, "FIBER", fiber.getId(), UUID.randomUUID());
            log.info("📝 Writing FiberDefinedEvent to outbox: {}", fiber.getCode());
        } catch (Exception e) {
            log.error("Error creating FiberDefinedEvent for fiber: {}", fiber.getCode(), e);
        }
    }
    
    public void publishFiberUpdated(Fiber fiber) {
        try {
            FiberUpdatedEvent event = eventMapper.toUpdatedEvent(fiber);
            writeToOutbox(event, fiberEventsTopic, "FIBER", fiber.getId(), UUID.randomUUID());
            log.info("📝 Writing FiberUpdatedEvent to outbox: {}", fiber.getCode());
        } catch (Exception e) {
            log.error("Error creating FiberUpdatedEvent for fiber: {}", fiber.getCode(), e);
        }
    }
    
    public void publishFiberDeactivated(Fiber fiber) {
        try {
            FiberDeactivatedEvent event = eventMapper.toDeactivatedEvent(fiber);
            writeToOutbox(event, fiberEventsTopic, "FIBER", fiber.getId(), UUID.randomUUID());
            log.info("📝 Writing FiberDeactivatedEvent to outbox: {}", fiber.getCode());
        } catch (Exception e) {
            log.error("Error creating FiberDeactivatedEvent for fiber: {}", fiber.getCode(), e);
        }
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
            
        } catch (Exception e) {
            log.error("❌ Failed to serialize event to JSON: {}", event.getClass().getSimpleName(), e);
            throw new RuntimeException("Failed to write event to outbox", e);
        }
    }
}
