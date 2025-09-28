package com.fabricmanagement.company.domain.event;

import com.fabricmanagement.company.domain.valueobject.CompanyStatus;
import com.fabricmanagement.company.domain.valueobject.CompanyType;
import com.fabricmanagement.company.domain.valueobject.Industry;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Domain event published when a company is updated.
 */
@Data
@Builder
public class CompanyUpdatedEvent {
    private UUID companyId;
    private UUID tenantId;
    private String companyName;
    private Industry industry;
    private CompanyType companyType;
    private CompanyStatus status;
    private LocalDateTime updatedAt;
    @Builder.Default
    private String eventType = "COMPANY_UPDATED";
    @Builder.Default
    private String version = "1.0";
}