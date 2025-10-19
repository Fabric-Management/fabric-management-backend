package com.fabricmanagement.fiber.infrastructure.messaging;

import com.fabricmanagement.fiber.application.mapper.FiberEventMapper;
import com.fabricmanagement.fiber.domain.aggregate.Fiber;
import com.fabricmanagement.fiber.domain.event.FiberDeactivatedEvent;
import com.fabricmanagement.fiber.domain.event.FiberDefinedEvent;
import com.fabricmanagement.fiber.domain.event.FiberUpdatedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Fiber Event Publisher
 * 
 * Publishes fiber domain events to Kafka.
 * Follows Event-Driven Architecture and Choreography patterns.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FiberEventPublisher {
    
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final FiberEventMapper eventMapper;
    private final ObjectMapper objectMapper;
    
    @Value("${app.kafka.topic.fiber-events:fiber-events}")
    private String fiberEventsTopic;
    
    public void publishFiberDefined(Fiber fiber) {
        try {
            FiberDefinedEvent event = eventMapper.toDefinedEvent(fiber);
            String eventJson = objectMapper.writeValueAsString(event);
            
            kafkaTemplate.send(fiberEventsTopic, fiber.getId().toString(), eventJson)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.info("Published FIBER_DEFINED event for fiber: {}", fiber.getCode());
                        } else {
                            log.error("Failed to publish FIBER_DEFINED event for fiber: {}", fiber.getCode(), ex);
                        }
                    });
        } catch (Exception e) {
            log.error("Error creating FIBER_DEFINED event for fiber: {}", fiber.getCode(), e);
        }
    }
    
    public void publishFiberUpdated(Fiber fiber) {
        try {
            FiberUpdatedEvent event = eventMapper.toUpdatedEvent(fiber);
            String eventJson = objectMapper.writeValueAsString(event);
            
            kafkaTemplate.send(fiberEventsTopic, fiber.getId().toString(), eventJson)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.info("Published FIBER_UPDATED event for fiber: {}", fiber.getCode());
                        } else {
                            log.error("Failed to publish FIBER_UPDATED event for fiber: {}", fiber.getCode(), ex);
                        }
                    });
        } catch (Exception e) {
            log.error("Error creating FIBER_UPDATED event for fiber: {}", fiber.getCode(), e);
        }
    }
    
    public void publishFiberDeactivated(Fiber fiber) {
        try {
            FiberDeactivatedEvent event = eventMapper.toDeactivatedEvent(fiber);
            String eventJson = objectMapper.writeValueAsString(event);
            
            kafkaTemplate.send(fiberEventsTopic, fiber.getId().toString(), eventJson)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.info("Published FIBER_DEACTIVATED event for fiber: {}", fiber.getCode());
                        } else {
                            log.error("Failed to publish FIBER_DEACTIVATED event for fiber: {}", fiber.getCode(), ex);
                        }
                    });
        } catch (Exception e) {
            log.error("Error creating FIBER_DEACTIVATED event for fiber: {}", fiber.getCode(), e);
        }
    }
}
