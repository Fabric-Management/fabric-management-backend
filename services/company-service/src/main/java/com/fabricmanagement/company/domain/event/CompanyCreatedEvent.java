package com.fabricmanagement.company.domain.event;

import com.fabricmanagement.company.domain.valueobject.CompanyStatus;
import com.fabricmanagement.company.domain.valueobject.CompanyType;
import com.fabricmanagement.company.domain.valueobject.Industry;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Domain event published when a company is created.
 */
@Data
@Builder
public class CompanyCreatedEvent {
    private UUID companyId;
    private UUID tenantId;
    private String companyName;
    private Industry industry;
    private CompanyType companyType;
    private CompanyStatus status;
    private LocalDateTime createdAt;
    @Builder.Default
    private String eventType = "COMPANY_CREATED";
    @Builder.Default
    private String version = "1.0";
}