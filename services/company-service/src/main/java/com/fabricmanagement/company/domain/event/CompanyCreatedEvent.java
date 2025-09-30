package com.fabricmanagement.company.domain.event;

import com.fabricmanagement.shared.domain.event.DomainEvent;
import lombok.Getter;
import lombok.ToString;

import java.util.UUID;

/**
 * Company Created Domain Event
 * 
 * Published when a new company is created in the system
 */
@Getter
@ToString
public class CompanyCreatedEvent extends DomainEvent {
    
    private final UUID companyId;
    private final UUID tenantId;
    private final String companyName;
    private final String companyType;
    private final String industry;

    public CompanyCreatedEvent(UUID companyId, UUID tenantId, String companyName,
                             String companyType, String industry) {
        super();
        this.companyId = companyId;
        this.tenantId = tenantId;
        this.companyName = companyName;
        this.companyType = companyType;
        this.industry = industry;
    }

    @Override
    public String getEventType() {
        return "CompanyCreated";
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
