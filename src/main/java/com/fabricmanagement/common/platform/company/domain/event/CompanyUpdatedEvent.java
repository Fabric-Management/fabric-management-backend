package com.fabricmanagement.common.platform.company.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import lombok.Getter;

import java.util.UUID;

/**
 * Event fired when company information is updated.
 *
 * <p>Listeners: Audit, Analytics, Cache invalidation</p>
 */
@Getter
public class CompanyUpdatedEvent extends DomainEvent {

    private final UUID companyId;
    private final String companyName;

    public CompanyUpdatedEvent(UUID tenantId, UUID companyId, String companyName) {
        super(tenantId, "COMPANY_UPDATED");
        this.companyId = companyId;
        this.companyName = companyName;
    }
}

