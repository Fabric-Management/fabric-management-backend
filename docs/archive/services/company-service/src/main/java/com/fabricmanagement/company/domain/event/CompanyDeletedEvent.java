package com.fabricmanagement.company.domain.event;

import com.fabricmanagement.shared.domain.event.DomainEvent;
import lombok.Getter;
import lombok.ToString;

import java.util.UUID;

/**
 * Company Deleted Domain Event
 * 
 * Published when a company is soft deleted from the system
 */
@Getter
@ToString
public class CompanyDeletedEvent extends DomainEvent {
    
    private final UUID companyId;
    private final UUID tenantId;
    private final String companyName;
    private final String companyType;

    public CompanyDeletedEvent(UUID companyId, UUID tenantId, String companyName,
                              String companyType) {
        super();
        this.companyId = companyId;
        this.tenantId = tenantId;
        this.companyName = companyName;
        this.companyType = companyType;
    }

    @Override
    public String getEventType() {
        return "CompanyDeleted";
    }

    @Override
    public String getAggregateId() {
        return companyId.toString();
    }

    @Override
    public String getTenantId() {
        return tenantId.toString();
    }
}
