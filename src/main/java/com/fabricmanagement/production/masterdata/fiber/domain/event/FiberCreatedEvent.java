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
    private final String fiberCode;
    private final String fiberName;
    private final UUID categoryId;
    private final UUID isoCodeId;
    
    public FiberCreatedEvent(
            UUID tenantId,
            UUID fiberId,
            String fiberCode,
            String fiberName,
            UUID categoryId,
            UUID isoCodeId) {
        super(tenantId, "FIBER_CREATED");
        this.fiberId = fiberId;
        this.fiberCode = fiberCode;
        this.fiberName = fiberName;
        this.categoryId = categoryId;
        this.isoCodeId = isoCodeId;
    }
}

