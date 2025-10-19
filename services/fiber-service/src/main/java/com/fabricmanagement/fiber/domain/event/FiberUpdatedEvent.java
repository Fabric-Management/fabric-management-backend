package com.fabricmanagement.fiber.domain.event;

import com.fabricmanagement.shared.domain.event.DomainEvent;
import lombok.*;

import java.util.Map;
import java.util.UUID;

/**
 * Event: Fiber Updated
 * 
 * Published when a fiber's properties are updated.
 * 
 * Consumers:
 * - Yarn Service: Updates fiber details in yarn compositions
 * - Analytics Service: Track fiber modifications
 */
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class FiberUpdatedEvent extends DomainEvent {
    
    private UUID fiberId;
    private UUID tenantId;
    private String code;
    private String name;
    private String category;
    private String compositionType;
    private String originType;
    private String sustainabilityType;
    private String status;
    private Map<String, Object> property;
    
    @Override
    public String getEventType() {
        return "FIBER_UPDATED";
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

