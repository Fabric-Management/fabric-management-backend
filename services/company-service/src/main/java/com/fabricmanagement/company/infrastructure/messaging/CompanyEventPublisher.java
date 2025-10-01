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
 * Publishes company domain events to Kafka
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CompanyEventPublisher {
    
    private static final String COMPANY_EVENTS_TOPIC = "company-events";
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    /**
     * Publishes CompanyCreatedEvent
     */
    public void publishCompanyCreated(CompanyCreatedEvent event) {
        log.info("Publishing CompanyCreatedEvent for company: {}", event.getCompanyId());
        
        CompletableFuture<SendResult<String, Object>> future = 
            kafkaTemplate.send(COMPANY_EVENTS_TOPIC, event.getCompanyId().toString(), event);
        
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("CompanyCreatedEvent published successfully: {}", event.getCompanyId());
            } else {
                log.error("Failed to publish CompanyCreatedEvent: {}", event.getCompanyId(), ex);
            }
        });
    }
    
    /**
     * Publishes CompanyUpdatedEvent
     */
    public void publishCompanyUpdated(CompanyUpdatedEvent event) {
        log.info("Publishing CompanyUpdatedEvent for company: {}", event.getCompanyId());
        
        CompletableFuture<SendResult<String, Object>> future = 
            kafkaTemplate.send(COMPANY_EVENTS_TOPIC, event.getCompanyId().toString(), event);
        
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("CompanyUpdatedEvent published successfully: {}", event.getCompanyId());
            } else {
                log.error("Failed to publish CompanyUpdatedEvent: {}", event.getCompanyId(), ex);
            }
        });
    }
    
    /**
     * Publishes CompanyDeletedEvent
     */
    public void publishCompanyDeleted(CompanyDeletedEvent event) {
        log.info("Publishing CompanyDeletedEvent for company: {}", event.getCompanyId());
        
        CompletableFuture<SendResult<String, Object>> future = 
            kafkaTemplate.send(COMPANY_EVENTS_TOPIC, event.getCompanyId().toString(), event);
        
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("CompanyDeletedEvent published successfully: {}", event.getCompanyId());
            } else {
                log.error("Failed to publish CompanyDeletedEvent: {}", event.getCompanyId(), ex);
            }
        });
    }
    
    /**
     * Publishes any domain event
     */
    public void publishEvent(Object event) {
        log.debug("Publishing event: {}", event.getClass().getSimpleName());
        
        if (event instanceof CompanyCreatedEvent) {
            publishCompanyCreated((CompanyCreatedEvent) event);
        } else if (event instanceof CompanyUpdatedEvent) {
            publishCompanyUpdated((CompanyUpdatedEvent) event);
        } else if (event instanceof CompanyDeletedEvent) {
            publishCompanyDeleted((CompanyDeletedEvent) event);
        } else {
            log.warn("Unknown event type: {}", event.getClass().getName());
        }
    }
}

