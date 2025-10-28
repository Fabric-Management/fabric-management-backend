package com.fabricmanagement.production.masterdata.fiber.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import lombok.Getter;

import java.util.UUID;

/**
 * Domain event published when a new Fiber is created.
 */
@Getter
public class FiberCreatedEvent extends DomainEvent {
    
    private final UUID fiberId;
    private final String fiberName;
    private final UUID fiberCategoryId;
    private final UUID fiberIsoCodeId;
    
    public FiberCreatedEvent(
            UUID tenantId,
            UUID fiberId,
            String fiberName,
            UUID fiberCategoryId,
            UUID fiberIsoCodeId) {
        super(tenantId, "FIBER_CREATED");
        this.fiberId = fiberId;
        this.fiberName = fiberName;
        this.fiberCategoryId = fiberCategoryId;
        this.fiberIsoCodeId = fiberIsoCodeId;
    }
}

