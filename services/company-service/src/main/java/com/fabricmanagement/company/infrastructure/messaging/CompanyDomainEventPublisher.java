package com.fabricmanagement.company.infrastructure.messaging;

import com.fabricmanagement.company.domain.aggregate.Company;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Company Domain Event Publisher
 * 
 * Publishes company domain events to Kafka via Outbox Pattern
 * 
 * Note: Removed CompanyEventStore (event sourcing not implemented)
 * Events are published to Kafka and persisted via Outbox Pattern only
 */
@Component("companyDomainEventPublisher")
@RequiredArgsConstructor
@Slf4j
public class CompanyDomainEventPublisher {
    
    private final CompanyEventPublisher companyEventPublisher;
    
    /**
     * Publishes domain events to Kafka after transaction commit
     */
    public void publishEvents(Company company) {
        List<Object> events = company.getAndClearDomainEvents();
        
        if (events.isEmpty()) {
            return;
        }
        
        log.debug("Publishing {} domain events for company: {}", events.size(), company.getId());
        
        for (Object event : events) {
            // Publish event to Kafka (via Outbox Pattern)
            companyEventPublisher.publishEvent(event);
        }
    }
}

