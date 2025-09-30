package com.fabricmanagement.company.domain.event;

import com.fabricmanagement.shared.domain.event.DomainEvent;
import lombok.Getter;
import lombok.ToString;

import java.util.UUID;

/**
 * Company Updated Domain Event
 * 
 * Published when a company's information is updated
 */
@Getter
@ToString
public class CompanyUpdatedEvent extends DomainEvent {
    
    private final UUID companyId;
    private final UUID tenantId;
    private final String companyName;
    private final String companyType;

    public CompanyUpdatedEvent(UUID companyId, UUID tenantId, String companyName,
                             String companyType) {
        super();
        this.companyId = companyId;
        this.tenantId = tenantId;
        this.companyName = companyName;
        this.companyType = companyType;
    }

    @Override
    public String getEventType() {
        return "CompanyUpdated";
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
