package com.fabricmanagement.production.masterdata.material.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import lombok.Getter;

import java.util.UUID;

/**
 * Event fired when a new material is created.
 *
 * <p>Listeners: Inventory, Analytics, Audit</p>
 */
@Getter
public class MaterialCreatedEvent extends DomainEvent {

    private final UUID materialId;
    private final MaterialType materialType;

    public MaterialCreatedEvent(UUID tenantId, UUID materialId, MaterialType materialType) {
        super(tenantId, "MATERIAL_CREATED");
        this.materialId = materialId;
        this.materialType = materialType;
    }
}

