package com.fabricmanagement.shared.domain.event.company;

import com.fabricmanagement.shared.domain.event.DomainEvent;
import lombok.*;

import java.util.UUID;

/**
 * Event: Company Created
 * 
 * Published by Company Service after successful company creation.
 * 
 * Consumers:
 * - User Service: (already listens) Links company to tenant
 * - Analytics Service: (future) Track company registrations
 * - Notification Service: (future) Send admin notifications
 * 
 * @since 3.1.0 - Event-Driven Refactor (Oct 13, 2025)
 */
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class CompanyCreatedEvent extends DomainEvent {
    
    private UUID companyId;
    private UUID tenantId;
    private String companyName;
    private String legalName;
    private String taxId;
    private String registrationNumber;
    private String companyType;
    private String industry;
    private String country;
    
    @Override
    public String getEventType() {
        return "COMPANY_CREATED";
    }
    
    @Override
    public String getAggregateId() {
        return companyId != null ? companyId.toString() : null;
    }
    
    @Override
    public String getTenantId() {
        return tenantId != null ? tenantId.toString() : null;
    }
}

