package com.fabricmanagement.shared.domain.event.tenant;

import com.fabricmanagement.shared.domain.event.DomainEvent;
import lombok.*;

import java.util.UUID;

/**
 * Event: Tenant Registration Completed
 * 
 * Published by User Service after successful tenant onboarding.
 * 
 * Consumers:
 * - Contact Service: Creates company address and admin contacts
 * - Company Service: (future) Initialize company settings, default policies
 * - Notification Service: (future) Send welcome email
 * 
 * Event-Driven Benefits:
 * ✅ Loose coupling - services don't directly call each other
 * ✅ Async processing - non-blocking
 * ✅ Retry/DLQ - automatic error handling
 * ✅ Scalability - easy to add new consumers
 * 
 * @since 3.1.0 - Event-Driven Refactor (Oct 13, 2025)
 */
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class TenantRegisteredEvent extends DomainEvent {
    
    private UUID tenantId;
    private UUID companyId;
    private UUID userId;
    
    // Company details for Contact Service
    private String companyName;
    private String companyLegalName;
    private String companyType;
    private String industry;
    private String country;
    
    // Address details for Contact Service
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String district;
    private String postalCode;
    
    // Admin user details for Contact Service
    private String adminEmail;
    private String adminPhone;
    private String adminFirstName;
    private String adminLastName;
    
    @Override
    public String getEventType() {
        return "TENANT_REGISTERED";
    }
    
    @Override
    public String getAggregateId() {
        return tenantId != null ? tenantId.toString() : null;
    }
    
    @Override
    public String getTenantId() {
        return tenantId != null ? tenantId.toString() : null;
    }
}

