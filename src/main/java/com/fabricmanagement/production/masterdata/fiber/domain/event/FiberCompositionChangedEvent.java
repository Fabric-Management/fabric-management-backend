package com.fabricmanagement.production.masterdata.fiber.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Domain event published when a Fiber's composition is changed.
 * 
 * <p>Contains map of base_fiber_id → percentage for blended fibers.</p>
 */
@Getter
public class FiberCompositionChangedEvent extends DomainEvent {
    
    private final UUID blendedFiberId;
    private final String fiberName;
    private final Map<UUID, BigDecimal> composition;  // baseFiberId → percentage
    
    public FiberCompositionChangedEvent(
            UUID tenantId,
            UUID blendedFiberId,
            String fiberName,
            Map<UUID, BigDecimal> composition) {
        super(tenantId, "FIBER_COMPOSITION_CHANGED");
        this.blendedFiberId = blendedFiberId;
        this.fiberName = fiberName;
        this.composition = composition;
    }
}

