package com.fabricmanagement.fiber.domain.event;

import com.fabricmanagement.shared.domain.event.DomainEvent;
import lombok.*;

import java.util.Map;
import java.util.UUID;

/**
 * Event: Fiber Defined
 * 
 * Published when a new fiber (pure or blend) is created.
 * 
 * Consumers:
 * - Yarn Service: Updates available fibers for yarn composition
 * - Analytics Service: Track fiber usage patterns
 */
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class FiberDefinedEvent extends DomainEvent {
    
    private UUID fiberId;
    private UUID tenantId;
    private String code;
    private String name;
    private String category;
    private String compositionType;
    private String originType;
    private String sustainabilityType;
    private String status;
    private Boolean isDefault;
    private Boolean reusable;
    private Map<String, Object> property;
    private Object components; // List of FiberComponent for blend fibers
    
    @Override
    public String getEventType() {
        return "FIBER_DEFINED";
    }
    
    @Override
    public String getAggregateId() {
        return fiberId != null ? fiberId.toString() : null;
    }
    
    @Override
    public String getTenantId() {
        return tenantId != null ? tenantId.toString() : null;
    }
}

