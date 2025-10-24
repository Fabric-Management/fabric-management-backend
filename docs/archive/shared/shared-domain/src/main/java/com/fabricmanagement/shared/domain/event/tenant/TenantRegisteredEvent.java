package com.fabricmanagement.shared.domain.event.tenant;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Tenant Registered Event
 * 
 * Domain event for tenant registration
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
public class TenantRegisteredEvent {
    
    private UUID eventId;
    private LocalDateTime occurredAt;
    private UUID tenantId;
    private String tenantName;
    private String tenantType;
    private String domain;
    private String status;
    private UUID registeredBy;
    private String traceId;
    private String correlationId;
    private Map<String, Object> metadata;
    
    /**
     * Create TenantRegisteredEvent
     */
    public static TenantRegisteredEvent create(UUID tenantId, String tenantName, 
                                             String tenantType, String domain, String status, 
                                             UUID registeredBy) {
        return TenantRegisteredEvent.builder()
            .eventId(UUID.randomUUID())
            .occurredAt(LocalDateTime.now())
            .tenantId(tenantId)
            .tenantName(tenantName)
            .tenantType(tenantType)
            .domain(domain)
            .status(status)
            .registeredBy(registeredBy)
            .traceId(UUID.randomUUID().toString())
            .correlationId(UUID.randomUUID().toString())
            .build();
    }
    
    /**
     * Create TenantRegisteredEvent with metadata
     */
    public static TenantRegisteredEvent create(UUID tenantId, String tenantName, 
                                             String tenantType, String domain, String status, 
                                             UUID registeredBy, Map<String, Object> metadata) {
        return TenantRegisteredEvent.builder()
            .eventId(UUID.randomUUID())
            .occurredAt(LocalDateTime.now())
            .tenantId(tenantId)
            .tenantName(tenantName)
            .tenantType(tenantType)
            .domain(domain)
            .status(status)
            .registeredBy(registeredBy)
            .traceId(UUID.randomUUID().toString())
            .correlationId(UUID.randomUUID().toString())
            .metadata(metadata)
            .build();
    }
}