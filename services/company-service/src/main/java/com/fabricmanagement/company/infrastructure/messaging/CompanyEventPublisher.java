package com.fabricmanagement.company.infrastructure.messaging;

import com.fabricmanagement.company.domain.event.CompanyCreatedEvent;
import com.fabricmanagement.company.domain.event.CompanyDeletedEvent;
import com.fabricmanagement.company.domain.event.CompanyUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

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
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CompanyEventPublisher {
    
    private static final String TOPIC = "company-events";
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    /**
     * Publishes CompanyCreatedEvent (async)
     */
    public void publishCompanyCreated(CompanyCreatedEvent event) {
        log.info("Publishing CompanyCreatedEvent for company: {}", event.getCompanyId());
        
        CompletableFuture<SendResult<String, Object>> future = 
            kafkaTemplate.send(TOPIC, event.getCompanyId().toString(), event);
        
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("✅ CompanyCreatedEvent published successfully: {}", event.getCompanyId());
            } else {
                log.error("❌ Failed to publish CompanyCreatedEvent: {}", event.getCompanyId(), ex);
            }
        });
    }
    
    /**
     * Publishes CompanyUpdatedEvent (async)
     */
    public void publishCompanyUpdated(CompanyUpdatedEvent event) {
        log.info("Publishing CompanyUpdatedEvent for company: {}", event.getCompanyId());
        
        CompletableFuture<SendResult<String, Object>> future = 
            kafkaTemplate.send(TOPIC, event.getCompanyId().toString(), event);
        
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("✅ CompanyUpdatedEvent published successfully: {}", event.getCompanyId());
            } else {
                log.error("❌ Failed to publish CompanyUpdatedEvent: {}", event.getCompanyId(), ex);
            }
        });
    }
    
    /**
     * Publishes CompanyDeletedEvent (async)
     */
    public void publishCompanyDeleted(CompanyDeletedEvent event) {
        log.info("Publishing CompanyDeletedEvent for company: {}", event.getCompanyId());
        
        CompletableFuture<SendResult<String, Object>> future = 
            kafkaTemplate.send(TOPIC, event.getCompanyId().toString(), event);
        
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("✅ CompanyDeletedEvent published successfully: {}", event.getCompanyId());
            } else {
                log.error("❌ Failed to publish CompanyDeletedEvent: {}", event.getCompanyId(), ex);
            }
        });
    }
}

