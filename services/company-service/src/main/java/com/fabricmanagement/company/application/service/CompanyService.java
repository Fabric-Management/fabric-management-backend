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
    
    // Domain Services & Repositories (for duplicate detection)
    private final com.fabricmanagement.company.domain.service.DuplicateCheckService duplicateCheckService;
    private final com.fabricmanagement.company.infrastructure.repository.CompanyRepository companyRepository;
    private final com.fabricmanagement.company.infrastructure.config.DuplicateDetectionConfig duplicateConfig;
    
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
            .businessType(request.getBusinessType())
            .parentCompanyId(request.getParentCompanyId() != null ? UUID.fromString(request.getParentCompanyId()) : null)
            .relationshipType(request.getRelationshipType())
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
    
    // =========================================================================
    // DUPLICATE PREVENTION METHODS
    // =========================================================================
    
    /**
     * Check for duplicate companies
     * 
     * Uses multiple strategies:
     * 1. Exact match on Tax ID and Registration Number
     * 2. Fuzzy matching on company names (Jaro-Winkler similarity)
     * 
     * @param request Company details to check
     * @param tenantId Tenant context
     * @return Duplicate check result
     */
    public CheckDuplicateResponse checkDuplicate(CheckDuplicateRequest request, UUID tenantId) {
        log.info("Checking duplicate for company: {}", request.getName());
        
        // Find potential duplicates from database
        List<com.fabricmanagement.company.domain.aggregate.Company> potentialDuplicates = 
            companyRepository.findPotentialDuplicates(
                tenantId, 
                request.getTaxId(), 
                request.getRegistrationNumber()
            );
        
        // Also search by name similarity using PostgreSQL trigram
        // Threshold configured in application.yml (NO MAGIC NUMBERS!)
        if (request.getName() != null && request.getName().trim().length() >= duplicateConfig.getFuzzySearchMinLength()) {
            List<com.fabricmanagement.company.domain.aggregate.Company> nameSimilar = 
                companyRepository.findSimilarCompanies(
                    tenantId, 
                    request.getName(), 
                    duplicateConfig.getDatabaseSearchThreshold()
                );
            
            // Merge the two lists (avoid duplicates)
            for (var company : nameSimilar) {
                if (potentialDuplicates.stream().noneMatch(c -> c.getId().equals(company.getId()))) {
                    potentialDuplicates.add(company);
                }
            }
        }
        
        // Use domain service for intelligent duplicate detection
        var result = duplicateCheckService.checkForDuplicates(
            tenantId,
            request.getName(),
            request.getTaxId(),
            request.getRegistrationNumber(),
            potentialDuplicates
        );
        
        // Map domain result to DTO
        if (!result.isDuplicate()) {
            return CheckDuplicateResponse.noDuplicate();
        }
        
        String recommendation = switch (result.getMatchType()) {
            case EXACT_TAX_ID -> "Cannot create company: Tax ID already exists. Please verify the tax ID or update the existing company.";
            case EXACT_REGISTRATION -> "Cannot create company: Registration number already exists. Please verify the registration number or update the existing company.";
            case FUZZY_NAME -> {
                if (result.getConfidence() >= 0.9) {
                    yield "Very similar company name found. This might be a duplicate. Please review before creating.";
                } else if (result.getConfidence() >= 0.8) {
                    yield "Similar company name found. Please verify this is not a duplicate.";
                } else {
                    yield "Possibly similar company name found. You may proceed with caution.";
                }
            }
            default -> "No duplicate found";
        };
        
        return CheckDuplicateResponse.builder()
            .isDuplicate(true)
            .matchType(result.getMatchType().name())
            .matchedCompanyId(result.getMatchedCompanyId().toString())
            .matchedCompanyName(result.getMatchedCompanyName())
            .confidence(result.getConfidence())
            .message(result.getMessage())
            .recommendation(recommendation)
            .build();
    }
    
    /**
     * Autocomplete search for companies (search-as-you-type)
     * 
     * @param query Search term
     * @param tenantId Tenant context
     * @return List of matching companies
     */
    public CompanyAutocompleteResponse autocomplete(String query, UUID tenantId) {
        log.debug("Autocomplete search: {}", query);
        
        // Configuration-driven validation (NO MAGIC NUMBERS!)
        if (query == null || query.trim().length() < duplicateConfig.getAutocompleteMinLength()) {
            return CompanyAutocompleteResponse.builder()
                .suggestions(List.of())
                .totalCount(0)
                .build();
        }
        
        // Use full-text search for fast results (if enabled)
        List<com.fabricmanagement.company.domain.aggregate.Company> companies;
        if (duplicateConfig.isEnableFullTextSearch()) {
            companies = companyRepository.searchCompaniesForAutocomplete(
                tenantId, 
                query, 
                duplicateConfig.getAutocompleteMaxResults()
            );
        } else {
            // Fallback to simple name search if full-text search disabled
            companies = companyRepository.searchByNameAndTenantId(query, tenantId).stream()
                .limit(duplicateConfig.getAutocompleteMaxResults())
                .toList();
        }
        
        List<CompanyAutocompleteResponse.CompanySuggestion> suggestions = companies.stream()
            .map(company -> CompanyAutocompleteResponse.CompanySuggestion.builder()
                .id(company.getId().toString())
                .name(company.getName().getValue())
                .legalName(company.getLegalName())
                .taxId(company.getTaxId())
                .type(company.getType().name())
                .industry(company.getIndustry().name())
                .build())
            .toList();
        
        return CompanyAutocompleteResponse.builder()
            .suggestions(suggestions)
            .totalCount(suggestions.size())
            .build();
    }
    
    /**
     * Find similar companies by name
     * 
     * @param name Company name to search for
     * @param threshold Minimum similarity (0.0 to 1.0)
     * @param tenantId Tenant context
     * @return List of similar companies
     */
    public List<CompanyResponse> findSimilar(String name, double threshold, UUID tenantId) {
        log.debug("Finding similar companies to: {} (threshold: {})", name, threshold);
        
        // Configuration-driven validation (NO MAGIC NUMBERS!)
        if (name == null || name.trim().length() < duplicateConfig.getFuzzySearchMinLength()) {
            return List.of();
        }
        
        // Use PostgreSQL trigram similarity
        List<com.fabricmanagement.company.domain.aggregate.Company> similar = 
            companyRepository.findSimilarCompanies(tenantId, name, threshold);
        
        // Map to response DTO using the standard mapper
        return similar.stream()
            .map(CompanyResponse::fromEntity)
            .collect(java.util.stream.Collectors.toList());
    }
}

