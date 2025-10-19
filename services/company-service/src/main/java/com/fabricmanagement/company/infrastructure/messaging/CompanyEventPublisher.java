package com.fabricmanagement.company.infrastructure.messaging;

import com.fabricmanagement.company.domain.event.CompanyCreatedEvent;
import com.fabricmanagement.company.domain.event.CompanyDeletedEvent;
import com.fabricmanagement.company.domain.event.CompanyUpdatedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Company Event Publisher
 * 
 * Publishes company domain events to Kafka (ASYNC).
 * 
 * Pattern:
 * - Non-blocking async publishing
 * - Error logging via CompletableFuture
 * - DLQ handling via BaseKafkaErrorConfig
 * 
 * ✅ ZERO HARDCODED (Manifesto compliance)
 * - Topic name from application.yml
 * - Override via ${KAFKA_TOPIC_COMPANY_EVENTS}
 * - Production-ready configuration
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CompanyEventPublisher {
    
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    // ✅ Config-driven topic name (ZERO HARDCODED!)
    @org.springframework.beans.factory.annotation.Value("${kafka.topics.company-events:company-events}")
    private String companyEventsTopic;
    
    /**
     * Publishes CompanyCreatedEvent (async)
     */
    public void publishCompanyCreated(CompanyCreatedEvent event) {
        log.info("Publishing CompanyCreatedEvent for company: {}", event.getCompanyId());
        
        try {
            // ✅ Envelope pattern: wrap event with metadata
            Map<String, Object> envelope = new HashMap<>();
            envelope.put("eventType", "CompanyCreated");
            envelope.put("data", event);
            
            String eventJson = objectMapper.writeValueAsString(envelope);
            
            CompletableFuture<SendResult<String, String>> future = 
                kafkaTemplate.send(companyEventsTopic, event.getCompanyId().toString(), eventJson);
            
            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("✅ CompanyCreatedEvent published successfully: {}", event.getCompanyId());
                } else {
                    log.error("❌ Failed to publish CompanyCreatedEvent: {}", event.getCompanyId(), ex);
                }
            });
        } catch (Exception e) {
            log.error("❌ Failed to serialize CompanyCreatedEvent: {}", event.getCompanyId(), e);
        }
    }
    
    /**
     * Publishes CompanyUpdatedEvent (async)
     */
    public void publishCompanyUpdated(CompanyUpdatedEvent event) {
        log.info("Publishing CompanyUpdatedEvent for company: {}", event.getCompanyId());
        
        try {
            // ✅ Envelope pattern: wrap event with metadata
            Map<String, Object> envelope = new HashMap<>();
            envelope.put("eventType", "CompanyUpdated");
            envelope.put("data", event);
            
            String eventJson = objectMapper.writeValueAsString(envelope);
            
            CompletableFuture<SendResult<String, String>> future = 
                kafkaTemplate.send(companyEventsTopic, event.getCompanyId().toString(), eventJson);
            
            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("✅ CompanyUpdatedEvent published successfully: {}", event.getCompanyId());
                } else {
                    log.error("❌ Failed to publish CompanyUpdatedEvent: {}", event.getCompanyId(), ex);
                }
            });
        } catch (Exception e) {
            log.error("❌ Failed to serialize CompanyUpdatedEvent: {}", event.getCompanyId(), e);
        }
    }
    
    /**
     * Publishes CompanyDeletedEvent (async)
     */
    public void publishCompanyDeleted(CompanyDeletedEvent event) {
        log.info("Publishing CompanyDeletedEvent for company: {}", event.getCompanyId());
        
        try {
            // ✅ Envelope pattern: wrap event with metadata
            Map<String, Object> envelope = new HashMap<>();
            envelope.put("eventType", "CompanyDeleted");
            envelope.put("data", event);
            
            String eventJson = objectMapper.writeValueAsString(envelope);
            
            CompletableFuture<SendResult<String, String>> future = 
                kafkaTemplate.send(companyEventsTopic, event.getCompanyId().toString(), eventJson);
            
            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("✅ CompanyDeletedEvent published successfully: {}", event.getCompanyId());
                } else {
                    log.error("❌ Failed to publish CompanyDeletedEvent: {}", event.getCompanyId(), ex);
                }
            });
        } catch (Exception e) {
            log.error("❌ Failed to serialize CompanyDeletedEvent: {}", event.getCompanyId(), e);
        }
    }
}

