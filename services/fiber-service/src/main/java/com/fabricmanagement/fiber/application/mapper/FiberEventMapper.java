package com.fabricmanagement.fiber.application.mapper;

import com.fabricmanagement.fiber.domain.aggregate.Fiber;
import com.fabricmanagement.fiber.domain.event.FiberDeactivatedEvent;
import com.fabricmanagement.fiber.domain.event.FiberDefinedEvent;
import com.fabricmanagement.fiber.domain.event.FiberUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Fiber Event Mapper
 * 
 * Maps Fiber domain events for Kafka publishing.
 * Follows Event-Driven Architecture patterns.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FiberEventMapper {
    
    public FiberDefinedEvent toDefinedEvent(Fiber fiber) {
        Map<String, Object> property = buildPropertyMap(fiber);
        
        FiberDefinedEvent event = new FiberDefinedEvent();
        event.setFiberId(fiber.getId());
        event.setTenantId(fiber.getTenantId());
        event.setCode(fiber.getCode());
        event.setName(fiber.getName());
        event.setCategory(fiber.getCategory().toString());
        event.setCompositionType(fiber.getCompositionType().toString());
        event.setOriginType(fiber.getOriginType().toString());
        event.setSustainabilityType(fiber.getSustainabilityType().toString());
        event.setStatus(fiber.getStatus().toString());
        event.setIsDefault(fiber.getIsDefault());
        event.setReusable(fiber.getReusable());
        event.setProperty(property);
        event.setComponents(fiber.getComponents());
        
        return event;
    }
    
    public FiberUpdatedEvent toUpdatedEvent(Fiber fiber) {
        Map<String, Object> property = buildPropertyMap(fiber);
        
        FiberUpdatedEvent event = new FiberUpdatedEvent();
        event.setFiberId(fiber.getId());
        event.setTenantId(fiber.getTenantId());
        event.setCode(fiber.getCode());
        event.setName(fiber.getName());
        event.setCategory(fiber.getCategory().toString());
        event.setCompositionType(fiber.getCompositionType().toString());
        event.setOriginType(fiber.getOriginType().toString());
        event.setSustainabilityType(fiber.getSustainabilityType().toString());
        event.setStatus(fiber.getStatus().toString());
        event.setProperty(property);
        
        return event;
    }
    
    public FiberDeactivatedEvent toDeactivatedEvent(Fiber fiber) {
        FiberDeactivatedEvent event = new FiberDeactivatedEvent();
        event.setFiberId(fiber.getId());
        event.setTenantId(fiber.getTenantId());
        event.setCode(fiber.getCode());
        event.setName(fiber.getName());
        event.setStatus(fiber.getStatus().toString());
        
        return event;
    }
    
    private Map<String, Object> buildPropertyMap(Fiber fiber) {
        if (fiber.getProperty() == null) {
            return null;
        }
        
        Map<String, Object> properties = new HashMap<>();
        properties.put("stapleLength", fiber.getProperty().getStapleLength());
        properties.put("fineness", fiber.getProperty().getFineness());
        properties.put("tenacity", fiber.getProperty().getTenacity());
        properties.put("moistureRegain", fiber.getProperty().getMoistureRegain());
        properties.put("color", fiber.getProperty().getColor());
        
        return properties;
    }
}
