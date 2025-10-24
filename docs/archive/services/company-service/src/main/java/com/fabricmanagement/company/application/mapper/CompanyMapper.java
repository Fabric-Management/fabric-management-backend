package com.fabricmanagement.company.application.mapper;

import com.fabricmanagement.company.api.dto.request.CreateCompanyRequest;
import com.fabricmanagement.company.api.dto.request.UpdateCompanyRequest;
import com.fabricmanagement.company.api.dto.response.CompanyResponse;
import com.fabricmanagement.company.domain.aggregate.Company;
import com.fabricmanagement.company.domain.valueobject.CompanyName;
import com.fabricmanagement.company.domain.valueobject.CompanyStatus;
import com.fabricmanagement.company.domain.valueobject.CompanyType;
import com.fabricmanagement.company.domain.valueobject.Industry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Mapper for Company entity
 * 
 * Handles DTO ↔ Entity transformations
 * Follows SRP: Only mapping logic, no business logic
 * Safe UUID/Enum parsing with fallbacks
 */
@Slf4j
@Component
public class CompanyMapper {

    /**
     * Maps CreateCompanyRequest to Company entity
     */
    public Company fromCreateRequest(CreateCompanyRequest request, UUID tenantId, String createdBy) {
        Company company = Company.builder()
                .tenantId(tenantId)
                .name(new CompanyName(request.getName()))
                .legalName(request.getLegalName())
                .taxId(request.getTaxId())
                .registrationNumber(request.getRegistrationNumber())
                .type(CompanyType.valueOf(request.getType()))
                .industry(Industry.valueOf(request.getIndustry()))
                .description(request.getDescription())
                .website(request.getWebsite())
                .logoUrl(request.getLogoUrl())
                .status(CompanyStatus.ACTIVE)
                .isActive(true)
                .maxUsers(10)
                .currentUsers(0)
                .subscriptionPlan("BASIC")
                .subscriptionStartDate(LocalDateTime.now())
                .subscriptionEndDate(LocalDateTime.now().plusYears(1))
                .createdBy(createdBy)
                .build();

        if (request.getBusinessType() != null && !request.getBusinessType().trim().isEmpty()) {
            try {
                company.setBusinessType(
                    com.fabricmanagement.shared.domain.policy.CompanyType.valueOf(request.getBusinessType())
                );
            } catch (IllegalArgumentException e) {
                log.warn("Invalid businessType: '{}', using default INTERNAL", request.getBusinessType());
                company.setBusinessType(com.fabricmanagement.shared.domain.policy.CompanyType.INTERNAL);
            }
        }
        
        // ✅ Safe UUID parsing for parentCompanyId
        if (request.getParentCompanyId() != null && !request.getParentCompanyId().trim().isEmpty()) {
            try {
                company.setParentCompanyId(UUID.fromString(request.getParentCompanyId().trim()));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid UUID for parentCompanyId: '{}', setting to null", request.getParentCompanyId());
                // Leave as null - optional field
            }
        }
        
        if (request.getRelationshipType() != null && !request.getRelationshipType().trim().isEmpty()) {
            company.setRelationshipType(request.getRelationshipType().trim());
        }
        
        company.setAddressLine1(request.getAddressLine1());
        company.setAddressLine2(request.getAddressLine2());
        company.setCity(request.getCity());
        company.setDistrict(request.getDistrict());
        company.setPostalCode(request.getPostalCode());
        if (request.getCountry() != null) {
            company.setCountry(request.getCountry());
        }

        return company;
    }

    /**
     * Updates Company entity from UpdateCompanyRequest
     */
    public void updateFromRequest(Company company, UpdateCompanyRequest request, String updatedBy) {
        if (request.getLegalName() != null) {
            company.setLegalName(request.getLegalName());
        }
        if (request.getDescription() != null) {
            company.setDescription(request.getDescription());
        }
        if (request.getWebsite() != null) {
            company.setWebsite(request.getWebsite());
        }
        if (request.getLogoUrl() != null) {
            company.setLogoUrl(request.getLogoUrl());
        }
        if (request.getTaxId() != null) {
            company.setTaxId(request.getTaxId());
        }
        if (request.getRegistrationNumber() != null) {
            company.setRegistrationNumber(request.getRegistrationNumber());
        }
        company.setUpdatedBy(updatedBy);
    }

    /**
     * Maps Company entity to CompanyResponse
     */
    public CompanyResponse toResponse(Company company) {
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
                .isPlatform(company.isPlatform())
                .addressLine1(company.getAddressLine1())
                .addressLine2(company.getAddressLine2())
                .city(company.getCity())
                .district(company.getDistrict())
                .postalCode(company.getPostalCode())
                .country(company.getCountry())
                .createdAt(company.getCreatedAt())
                .updatedAt(company.getUpdatedAt())
                .createdBy(company.getCreatedBy())
                .updatedBy(company.getUpdatedBy())
                .build();
    }
}

