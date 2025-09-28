package com.fabricmanagement.company.domain.event;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Domain event published when a company is deleted.
 */
@Data
@Builder
public class CompanyDeletedEvent {
    private UUID companyId;
    private UUID tenantId;
    private String companyName;
    private LocalDateTime deletedAt;
    @Builder.Default
    private String eventType = "COMPANY_DELETED";
    @Builder.Default
    private String version = "1.0";
}