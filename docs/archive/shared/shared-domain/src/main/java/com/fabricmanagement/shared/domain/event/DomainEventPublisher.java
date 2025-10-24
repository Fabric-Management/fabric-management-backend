package com.fabricmanagement.shared.domain.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Domain Event Publisher
 * 
 * Publishes domain events to Kafka topics for event-driven architecture
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DomainEventPublisher {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final String DOMAIN_EVENTS_TOPIC = "domain-events";
    
    /**
     * Publishes a domain event to Kafka
     * 
     * @param event the domain event to publish
     */
    public void publish(Object event) {
        if (event == null) {
            log.warn("Attempted to publish null event");
            return;
        }
        
        try {
            String eventType = event.getClass().getSimpleName();
            log.debug("Publishing domain event: {}", eventType);
            
            kafkaTemplate.send(DOMAIN_EVENTS_TOPIC, eventType, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish event {}: {}", eventType, ex.getMessage(), ex);
                    } else {
                        log.debug("Successfully published event: {}", eventType);
                    }
                });
        } catch (Exception e) {
            log.error("Error publishing domain event: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Publishes a domain event to a specific topic
     * 
     * @param topic the Kafka topic
     * @param event the domain event to publish
     */
    public void publish(String topic, Object event) {
        if (event == null) {
            log.warn("Attempted to publish null event to topic: {}", topic);
            return;
        }
        
        try {
            String eventType = event.getClass().getSimpleName();
            log.debug("Publishing domain event to topic {}: {}", topic, eventType);
            
            kafkaTemplate.send(topic, eventType, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish event {} to topic {}: {}", eventType, topic, ex.getMessage(), ex);
                    } else {
                        log.debug("Successfully published event to topic {}: {}", topic, eventType);
                    }
                });
        } catch (Exception e) {
            log.error("Error publishing domain event to topic {}: {}", topic, e.getMessage(), e);
        }
    }
}

