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
    private final String materialName;
    private final MaterialType materialType;
    private final String materialCode;

    public MaterialCreatedEvent(UUID tenantId, UUID materialId, String materialName, 
                               MaterialType materialType, String materialCode) {
        super(tenantId, "MATERIAL_CREATED");
        this.materialId = materialId;
        this.materialName = materialName;
        this.materialType = materialType;
        this.materialCode = materialCode;
    }
}

