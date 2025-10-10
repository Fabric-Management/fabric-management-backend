package com.fabricmanagement.company.infrastructure.messaging;

import com.fabricmanagement.company.domain.event.CompanyCreatedEvent;
import com.fabricmanagement.company.domain.event.CompanyDeletedEvent;
import com.fabricmanagement.company.domain.event.CompanyUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Company Event Publisher
 * 
 * Publishes company domain events to Kafka
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CompanyEventPublisher {
    
    private static final String TOPIC = "company-events";
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    public void publishCompanyCreated(CompanyCreatedEvent event) {
        log.info("Publishing CompanyCreatedEvent for company: {}", event.getCompanyId());
        kafkaTemplate.send(TOPIC, event.getCompanyId().toString(), event);
    }
    
    public void publishCompanyUpdated(CompanyUpdatedEvent event) {
        log.info("Publishing CompanyUpdatedEvent for company: {}", event.getCompanyId());
        kafkaTemplate.send(TOPIC, event.getCompanyId().toString(), event);
    }
    
    public void publishCompanyDeleted(CompanyDeletedEvent event) {
        log.info("Publishing CompanyDeletedEvent for company: {}", event.getCompanyId());
        kafkaTemplate.send(TOPIC, event.getCompanyId().toString(), event);
    }
}
