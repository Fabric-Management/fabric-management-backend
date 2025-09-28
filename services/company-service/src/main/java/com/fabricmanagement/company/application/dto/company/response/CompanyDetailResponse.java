package com.fabricmanagement.company.application.dto.company.response;

import com.fabricmanagement.company.domain.valueobject.*;
import com.fabricmanagement.company.infrastructure.integration.contact.CompanyContactResponse;
import com.fabricmanagement.company.infrastructure.integration.user.UserBasicResponse;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Detailed response DTO for company information with cross-service data.
 * Includes data from contact-service and user-service for complete company view.
 */
@Data
@Builder
public class CompanyDetailResponse {
    // Basic company information
    private UUID id;
    private String companyName;
    private String displayName;
    private String description;
    private String registrationNumber;
    private String taxNumber;
    private LocalDate foundedDate;

    // Company classification
    private Industry industry;
    private CompanyType companyType;
    private CompanySize companySize;
    private CompanyStatus status;

    // Financial information
    private BigDecimal annualRevenue;
    private Currency currency;
    private String fiscalYearEnd;

    // Business relationships and certifications
    private String website;
    private Set<String> certifications;
    private Set<String> businessLicenses;

    // Cross-service data (populated by application service)
    private CompanyContactResponse contactInfo; // From contact-service
    private List<UserBasicResponse> employees; // From user-service

    // Subsidiary information
    private Set<UUID> subsidiaryIds;
    private UUID parentCompanyId;

    // Audit fields
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;

    // Derived fields
    public String getFullCompanyName() {
        if (displayName != null && !displayName.trim().isEmpty()) {
            return displayName;
        }
        return companyName != null ? companyName : "";
    }

    public boolean hasContactInfo() {
        return contactInfo != null;
    }

    public boolean hasEmployees() {
        return employees != null && !employees.isEmpty();
    }

    public boolean hasSubsidiaries() {
        return subsidiaryIds != null && !subsidiaryIds.isEmpty();
    }

    public boolean isSubsidiary() {
        return parentCompanyId != null;
    }
}