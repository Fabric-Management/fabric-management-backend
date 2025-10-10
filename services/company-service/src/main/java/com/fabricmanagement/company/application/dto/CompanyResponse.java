package com.fabricmanagement.company.application.dto;

import com.fabricmanagement.company.domain.aggregate.Company;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for Company
 * 
 * Implements Serializable for Redis caching support
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyResponse implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private UUID id;
    private UUID tenantId;
    private String name;
    private String legalName;
    private String taxId;
    private String registrationNumber;
    private String type;
    private String industry;
    private String status;
    private String description;
    private String website;
    private String logoUrl;
    private Map<String, Object> settings;
    private Map<String, Object> preferences;
    private LocalDateTime subscriptionStartDate;
    private LocalDateTime subscriptionEndDate;
    private String subscriptionPlan;
    private boolean isActive;
    private int maxUsers;
    private int currentUsers;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    
    /**
     * Converts Company entity to CompanyResponse DTO
     */
    public static CompanyResponse fromEntity(Company company) {
        return CompanyResponse.builder()
            .id(company.getId())
            .tenantId(company.getTenantId())
            .name(company.getName().getValue())
            .legalName(company.getLegalName())
            .taxId(company.getTaxId())
            .registrationNumber(company.getRegistrationNumber())
            .type(company.getType().toString())
            .industry(company.getIndustry().toString())
            .status(company.getStatus().toString())
            .description(company.getDescription())
            .website(company.getWebsite())
            .logoUrl(company.getLogoUrl())
            .settings(company.getSettings())
            .preferences(company.getPreferences())
            .subscriptionStartDate(company.getSubscriptionStartDate())
            .subscriptionEndDate(company.getSubscriptionEndDate())
            .subscriptionPlan(company.getSubscriptionPlan())
            .isActive(company.isActive())
            .maxUsers(company.getMaxUsers())
            .currentUsers(company.getCurrentUsers())
            .createdAt(company.getCreatedAt())
            .updatedAt(company.getUpdatedAt())
            .createdBy(company.getCreatedBy())
            .updatedBy(company.getUpdatedBy())
            .build();
    }
}

