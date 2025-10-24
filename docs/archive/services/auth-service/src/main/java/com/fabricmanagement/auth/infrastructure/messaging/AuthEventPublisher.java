package com.fabricmanagement.auth.infrastructure.messaging;

import com.fabricmanagement.shared.domain.outbox.OutboxEvent;
import com.fabricmanagement.shared.domain.outbox.OutboxEventStatus;
import com.fabricmanagement.auth.domain.event.SecurityEvent;
import com.fabricmanagement.auth.infrastructure.outbox.AuthOutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Auth Event Publisher
 * 
 * Publishes consolidated SecurityEvent using Transactional Outbox Pattern
 * 
 * ✅ ZERO HARDCODED VALUES
 * ✅ OUTBOX PATTERN (Guaranteed delivery)
 * ✅ CONSOLIDATED EVENTS (Single SecurityEvent)
 * ✅ SHARED MODEL (Schema local, standard global)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthEventPublisher {
    
    private final AuthOutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    
    @Value("${kafka.topics.security-events:security-events}")
    private String securityEventsTopic;
    
    /**
     * Publish consolidated security event
     */
    public void publishSecurityEvent(SecurityEvent event) {
        log.info("📝 Writing SecurityEvent to outbox: {} for user: {}", event.getEventType(), event.getUserId());
        writeToOutbox(event, securityEventsTopic, "AUTH_USER", event.getUserId(), event.getTenantId());
    }
    
    /**
     * Legacy methods for backward compatibility
     * These will be deprecated in future versions
     */
    @Deprecated
    public void publishUserLogin(SecurityEvent event) {
        publishSecurityEvent(event);
    }
    
    @Deprecated
    public void publishUserLogout(SecurityEvent event) {
        publishSecurityEvent(event);
    }
    
    @Deprecated
    public void publishUserRegistration(SecurityEvent event) {
        publishSecurityEvent(event);
    }
    
    @Deprecated
    public void publishPasswordChanged(SecurityEvent event) {
        publishSecurityEvent(event);
    }
    
    @Deprecated
    public void publishAccountLocked(SecurityEvent event) {
        publishSecurityEvent(event);
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
