package com.fabricmanagement.company.infrastructure.messaging;

import com.fabricmanagement.company.domain.aggregate.Company;
import com.fabricmanagement.company.infrastructure.persistence.CompanyEventStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Domain Event Publisher
 * 
 * Listens to entity save events and publishes domain events
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DomainEventPublisher {
    
    private final CompanyEventPublisher companyEventPublisher;
    private final CompanyEventStore companyEventStore;
    
    /**
     * Publishes domain events after transaction commit
     */
    public void publishEvents(Company company) {
        List<Object> events = company.getAndClearDomainEvents();
        
        if (events.isEmpty()) {
            return;
        }
        
        log.debug("Publishing {} domain events for company: {}", events.size(), company.getId());
        
        for (Object event : events) {
            // Store event in event store
            companyEventStore.storeEvent(company.getId(), event);
            
            // Publish event to Kafka
            companyEventPublisher.publishEvent(event);
        }
    }
}

