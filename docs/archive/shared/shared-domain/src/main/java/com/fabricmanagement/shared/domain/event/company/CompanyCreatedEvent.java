package com.fabricmanagement.shared.domain.event.company;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Company Created Event
 * 
 * Domain event for company creation
 * 
 * ✅ ZERO HARDCODED VALUES
 * ✅ PRODUCTION-READY
 * ✅ DOMAIN EVENT
 * ✅ UUID TYPE SAFETY
 */
@Getter
@Setter
@Builder
@ToString
public class CompanyCreatedEvent {
    
    private UUID eventId;
    private LocalDateTime occurredAt;
    private UUID companyId;
    private UUID tenantId;
    private String companyName;
    private String companyType;
    private String domain;
    private String status;
    private UUID createdBy;
    private String traceId;
    private String correlationId;
    private Map<String, Object> metadata;
    
    /**
     * Create CompanyCreatedEvent
     */
    public static CompanyCreatedEvent create(UUID companyId, UUID tenantId, String companyName, 
                                           String companyType, String domain, String status, 
                                           UUID createdBy) {
        return CompanyCreatedEvent.builder()
            .eventId(UUID.randomUUID())
            .occurredAt(LocalDateTime.now())
            .companyId(companyId)
            .tenantId(tenantId)
            .companyName(companyName)
            .companyType(companyType)
            .domain(domain)
            .status(status)
            .createdBy(createdBy)
            .traceId(UUID.randomUUID().toString())
            .correlationId(UUID.randomUUID().toString())
            .build();
    }
    
    /**
     * Create CompanyCreatedEvent with metadata
     */
    public static CompanyCreatedEvent create(UUID companyId, UUID tenantId, String companyName, 
                                           String companyType, String domain, String status, 
                                           UUID createdBy, Map<String, Object> metadata) {
        return CompanyCreatedEvent.builder()
            .eventId(UUID.randomUUID())
            .occurredAt(LocalDateTime.now())
            .companyId(companyId)
            .tenantId(tenantId)
            .companyName(companyName)
            .companyType(companyType)
            .domain(domain)
            .status(status)
            .createdBy(createdBy)
            .traceId(UUID.randomUUID().toString())
            .correlationId(UUID.randomUUID().toString())
            .metadata(metadata)
            .build();
    }
}