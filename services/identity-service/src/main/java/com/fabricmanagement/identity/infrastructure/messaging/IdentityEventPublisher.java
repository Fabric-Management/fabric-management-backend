package com.fabricmanagement.identity.infrastructure.messaging;

import com.fabricmanagement.identity.application.port.out.IdentityEventPublisherPort;
import com.fabricmanagement.identity.domain.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Single Responsibility: Event publishing only
 * Open/Closed: Can be extended without modification
 * Adapter pattern implementation for event publishing
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IdentityEventPublisher implements IdentityEventPublisherPort {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publish(DomainEvent event) {
        log.info("Publishing event: {} for user: {}", event.getEventType(), event.getUserId());
        
        try {
            kafkaTemplate.send("identity-events", event.getUserId(), event);
            log.info("Event published successfully: {}", event.getEventType());
        } catch (Exception e) {
            log.error("Failed to publish event: {}", event.getEventType(), e);
            throw new RuntimeException("Failed to publish event", e);
        }
    }

    @Override
    public void publishAll(Iterable<DomainEvent> events) {
        log.info("Publishing multiple events");
        
        for (DomainEvent event : events) {
            publish(event);
        }
        
        log.info("All events published successfully");
    }
}