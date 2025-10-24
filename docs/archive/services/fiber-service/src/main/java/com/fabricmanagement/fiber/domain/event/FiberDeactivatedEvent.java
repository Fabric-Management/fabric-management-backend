package com.fabricmanagement.fiber.domain.event;

import com.fabricmanagement.shared.domain.event.DomainEvent;
import lombok.*;

import java.util.UUID;

/**
 * Event: Fiber Deactivated
 * 
 * Published when a fiber is deactivated (soft deleted).
 * 
 * Consumers:
 * - Yarn Service: Marks fiber as unavailable for new yarn compositions
 * - Analytics Service: Track fiber lifecycle
 */
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class FiberDeactivatedEvent extends DomainEvent {
    
    private UUID fiberId;
    private UUID tenantId;
    private String code;
    private String name;
    private String status;
    
    @Override
    public String getEventType() {
        return "FIBER_DEACTIVATED";
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

