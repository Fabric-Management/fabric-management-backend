package com.fabricmanagement.company.application.service;

import com.fabricmanagement.company.application.command.*;
import com.fabricmanagement.company.application.command.handler.*;
import com.fabricmanagement.company.application.dto.*;
import com.fabricmanagement.company.application.query.*;
import com.fabricmanagement.company.application.query.handler.*;
import com.fabricmanagement.company.domain.valueobject.CompanyStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Company Service
 * 
 * Main service for company management operations
 * Orchestrates command and query handlers
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyService {
    
    // Command Handlers
    private final CreateCompanyCommandHandler createCompanyCommandHandler;
    private final UpdateCompanyCommandHandler updateCompanyCommandHandler;
    private final DeleteCompanyCommandHandler deleteCompanyCommandHandler;
    private final UpdateCompanySettingsCommandHandler updateCompanySettingsCommandHandler;
    private final UpdateSubscriptionCommandHandler updateSubscriptionCommandHandler;
    private final ActivateCompanyCommandHandler activateCompanyCommandHandler;
    private final DeactivateCompanyCommandHandler deactivateCompanyCommandHandler;
    
    // Query Handlers
    private final GetCompanyQueryHandler getCompanyQueryHandler;
    private final ListCompaniesQueryHandler listCompaniesQueryHandler;
    private final SearchCompaniesQueryHandler searchCompaniesQueryHandler;
    private final GetCompaniesByStatusQueryHandler getCompaniesByStatusQueryHandler;
    
    /**
     * Creates a new company
     */
    @CacheEvict(value = {"companies", "companiesList"}, allEntries = true)
    public UUID createCompany(CreateCompanyRequest request, UUID tenantId, String createdBy) {
        log.info("Creating company: {} for tenant: {}", request.getName(), tenantId);
        
        CreateCompanyCommand command = CreateCompanyCommand.builder()
            .tenantId(tenantId)
            .name(request.getName())
            .legalName(request.getLegalName())
            .taxId(request.getTaxId())
            .registrationNumber(request.getRegistrationNumber())
            .type(request.getType())
            .industry(request.getIndustry())
            .description(request.getDescription())
            .website(request.getWebsite())
            .logoUrl(request.getLogoUrl())
            .createdBy(createdBy)
            .build();
        
        return createCompanyCommandHandler.handle(command);
    }
    
    /**
     * Updates company information
     */
    @CacheEvict(value = {"companies", "companiesList"}, allEntries = true)
    public void updateCompany(UUID companyId, UpdateCompanyRequest request, UUID tenantId, String updatedBy) {
        log.info("Updating company: {}", companyId);
        
        UpdateCompanyCommand command = UpdateCompanyCommand.builder()
            .companyId(companyId)
            .tenantId(tenantId)
            .legalName(request.getLegalName())
            .description(request.getDescription())
            .website(request.getWebsite())
            .logoUrl(request.getLogoUrl())
            .taxId(request.getTaxId())
            .registrationNumber(request.getRegistrationNumber())
            .updatedBy(updatedBy)
            .build();
        
        updateCompanyCommandHandler.handle(command);
    }
    
    /**
     * Deletes a company (soft delete)
     */
    @CacheEvict(value = {"companies", "companiesList"}, allEntries = true)
    public void deleteCompany(UUID companyId, UUID tenantId, String deletedBy) {
        log.info("Deleting company: {}", companyId);
        
        DeleteCompanyCommand command = DeleteCompanyCommand.builder()
            .companyId(companyId)
            .tenantId(tenantId)
            .deletedBy(deletedBy)
            .build();
        
        deleteCompanyCommandHandler.handle(command);
    }
    
    /**
     * Updates company settings
     */
    @CacheEvict(value = {"companies", "companiesList"}, allEntries = true)
    public void updateSettings(UUID companyId, UpdateSettingsRequest request, UUID tenantId, String updatedBy) {
        log.info("Updating settings for company: {}", companyId);
        
        UpdateCompanySettingsCommand command = UpdateCompanySettingsCommand.builder()
            .companyId(companyId)
            .tenantId(tenantId)
            .settings(request.getSettings())
            .updatedBy(updatedBy)
            .build();
        
        updateCompanySettingsCommandHandler.handle(command);
    }
    
    /**
     * Updates company settings
     */
    @CacheEvict(value = {"companies", "companiesList"}, allEntries = true)
    public void updateCompanySettings(UUID companyId, UpdateCompanySettingsRequest request, 
                                     UUID tenantId, String updatedBy) {
        log.info("Updating settings for company: {} by user: {}", companyId, updatedBy);
        
        // Merge all settings into a single map
        java.util.Map<String, Object> allSettings = new java.util.HashMap<>();
        if (request.getSettings() != null) {
            allSettings.putAll(request.getSettings());
        }
        if (request.getPreferences() != null) {
            allSettings.put("preferences", request.getPreferences());
        }
        if (request.getTimezone() != null) {
            allSettings.put("timezone", request.getTimezone());
        }
        if (request.getLanguage() != null) {
            allSettings.put("language", request.getLanguage());
        }
        if (request.getCurrency() != null) {
            allSettings.put("currency", request.getCurrency());
        }
        
        UpdateCompanySettingsCommand command = UpdateCompanySettingsCommand.builder()
            .companyId(companyId)
            .tenantId(tenantId)
            .settings(allSettings)
            .updatedBy(updatedBy)
            .build();
        
        updateCompanySettingsCommandHandler.handle(command);
    }
    
    /**
     * Updates company subscription
     */
    @CacheEvict(value = {"companies", "companiesList"}, allEntries = true)
    public void updateSubscription(UUID companyId, UpdateSubscriptionRequest request, UUID tenantId, String updatedBy) {
        log.info("Updating subscription for company: {}", companyId);
        
        UpdateSubscriptionCommand command = UpdateSubscriptionCommand.builder()
            .companyId(companyId)
            .tenantId(tenantId)
            .plan(request.getPlan())
            .maxUsers(request.getMaxUsers())
            .endDate(request.getEndDate())
            .updatedBy(updatedBy)
            .build();
        
        updateSubscriptionCommandHandler.handle(command);
    }
    
    /**
     * Activates a company
     */
    @CacheEvict(value = {"companies", "companiesList"}, allEntries = true)
    public void activateCompany(UUID companyId, UUID tenantId, String updatedBy) {
        log.info("Activating company: {}", companyId);
        
        ActivateCompanyCommand command = ActivateCompanyCommand.builder()
            .companyId(companyId)
            .tenantId(tenantId)
            .updatedBy(updatedBy)
            .build();
        
        activateCompanyCommandHandler.handle(command);
    }
    
    /**
     * Deactivates a company
     */
    @CacheEvict(value = {"companies", "companiesList"}, allEntries = true)
    public void deactivateCompany(UUID companyId, UUID tenantId, String updatedBy) {
        log.info("Deactivating company: {}", companyId);
        
        DeactivateCompanyCommand command = DeactivateCompanyCommand.builder()
            .companyId(companyId)
            .tenantId(tenantId)
            .updatedBy(updatedBy)
            .build();
        
        deactivateCompanyCommandHandler.handle(command);
    }
    
    /**
     * Gets a company by ID
     */
    @Cacheable(value = "companies", key = "#companyId")
    public CompanyResponse getCompany(UUID companyId, UUID tenantId) {
        log.debug("Getting company: {}", companyId);
        
        GetCompanyQuery query = GetCompanyQuery.builder()
            .companyId(companyId)
            .tenantId(tenantId)
            .build();
        
        return getCompanyQueryHandler.handle(query);
    }
    
    /**
     * Lists all companies for a tenant
     */
    @Cacheable(value = "companiesList", key = "#tenantId")
    public List<CompanyResponse> listCompanies(UUID tenantId) {
        log.debug("Listing companies for tenant: {}", tenantId);
        
        ListCompaniesQuery query = ListCompaniesQuery.builder()
            .tenantId(tenantId)
            .build();
        
        return listCompaniesQueryHandler.handle(query);
    }
    
    /**
     * Searches companies by criteria
     */
    public List<CompanyResponse> searchCompanies(UUID tenantId, String name, 
                                                 String industry, String companyType) {
        log.debug("Searching companies with criteria: name={}, industry={}, companyType={} for tenant: {}", 
                 name, industry, companyType, tenantId);
        
        SearchCompaniesQuery query = SearchCompaniesQuery.builder()
            .tenantId(tenantId)
            .searchTerm(name)
            .build();
        
        return searchCompaniesQueryHandler.handle(query);
    }
    
    /**
     * Gets companies by status
     */
    public List<CompanyResponse> getCompaniesByStatus(CompanyStatus status, UUID tenantId) {
        log.debug("Getting companies with status: {} for tenant: {}", status, tenantId);
        
        GetCompaniesByStatusQuery query = GetCompaniesByStatusQuery.builder()
            .tenantId(tenantId)
            .status(status)
            .build();
        
        return getCompaniesByStatusQueryHandler.handle(query);
    }
}

