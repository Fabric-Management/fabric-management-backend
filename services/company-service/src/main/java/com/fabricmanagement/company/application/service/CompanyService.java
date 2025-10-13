package com.fabricmanagement.company.application.service;

import com.fabricmanagement.company.api.dto.request.CreateCompanyRequest;
import com.fabricmanagement.company.api.dto.request.UpdateCompanyRequest;
import com.fabricmanagement.company.api.dto.request.UpdateCompanySettingsRequest;
import com.fabricmanagement.company.api.dto.request.UpdateSubscriptionRequest;
import com.fabricmanagement.company.api.dto.request.CheckDuplicateRequest;
import com.fabricmanagement.company.api.dto.response.CompanyResponse;
import com.fabricmanagement.company.api.dto.response.CheckDuplicateResponse;
import com.fabricmanagement.company.api.dto.response.CompanyAutocompleteResponse;
import com.fabricmanagement.company.api.dto.response.CompanySimilarResponse;
import com.fabricmanagement.shared.application.response.PagedResponse;
import com.fabricmanagement.company.application.mapper.CompanyMapper;
import com.fabricmanagement.company.application.mapper.CompanyEventMapper;
import com.fabricmanagement.company.domain.aggregate.Company;
import com.fabricmanagement.company.domain.exception.CompanyAlreadyExistsException;
import com.fabricmanagement.company.domain.exception.CompanyNotFoundException;
import com.fabricmanagement.company.domain.valueobject.CompanyStatus;
import com.fabricmanagement.company.infrastructure.repository.CompanyRepository;
import com.fabricmanagement.company.infrastructure.config.DuplicateDetectionConfig;
import com.fabricmanagement.company.infrastructure.messaging.CompanyEventPublisher;
import com.fabricmanagement.shared.infrastructure.util.StringNormalizationUtil;
import com.fabricmanagement.shared.infrastructure.util.TokenBasedSimilarityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Company Service
 * 
 * Business logic only - NO mapping!
 * Mapping → CompanyMapper
 * Events → CompanyEventMapper
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyService {
    
    private final CompanyRepository companyRepository;
    private final CompanyMapper companyMapper;
    private final CompanyEventMapper eventMapper;
    private final CompanyEventPublisher eventPublisher;
    private final DuplicateDetectionConfig duplicateConfig;
    private final StringNormalizationUtil normalizationUtil;
    private final TokenBasedSimilarityUtil tokenSimilarityUtil;
    
    @Transactional
    @CacheEvict(value = {"companies", "companiesList"}, allEntries = true)
    public UUID createCompany(CreateCompanyRequest request, UUID tenantId, String createdBy) {
        log.info("Creating company: {} for tenant: {}", request.getName(), tenantId);
        
        // Check for duplicate tax ID (CRITICAL - legal requirement)
        if (request.getTaxId() != null && !request.getTaxId().isBlank()) {
            companyRepository.findByTaxIdAndTenantId(request.getTaxId(), tenantId)
                .ifPresent(existing -> {
                    throw new CompanyAlreadyExistsException(
                        "TAX_ID",
                        request.getTaxId(),
                        "A company with this tax ID is already registered. If this is your company, please use the forgot password option to recover your account."
                    );
                });
        }
        
        // Check for duplicate registration number (CRITICAL - legal requirement)
        if (request.getRegistrationNumber() != null && !request.getRegistrationNumber().isBlank()) {
            companyRepository.findByRegistrationNumberAndTenantId(request.getRegistrationNumber(), tenantId)
                .ifPresent(existing -> {
                    throw new CompanyAlreadyExistsException(
                        "REGISTRATION_NUMBER",
                        request.getRegistrationNumber(),
                        "A company with this registration number is already registered. If this is your company, please use the forgot password option to recover your account."
                    );
                });
        }
        
        // Check for duplicate name (less strict - exact match only)
        if (companyRepository.existsByNameAndTenantId(request.getName(), tenantId)) {
            throw new CompanyAlreadyExistsException(
                "NAME_EXACT",
                request.getName(),
                "A company with this name is already registered. If this is your company, please use the forgot password option to recover your account."
            );
        }
        
        Company company = companyMapper.fromCreateRequest(request, tenantId, createdBy);
        company = companyRepository.save(company);
        
        log.info("Company created successfully with id: {}", company.getId());
        
        eventPublisher.publishCompanyCreated(eventMapper.toCreatedEvent(company));
        
        return company.getId();
    }
    
    @Transactional
    @CacheEvict(value = {"companies", "companiesList"}, allEntries = true)
    public void updateCompany(UUID companyId, UpdateCompanyRequest request, UUID tenantId, String updatedBy) {
        log.info("Updating company: {}", companyId);
        
        Company company = getCompanyEntity(companyId, tenantId);
        companyMapper.updateFromRequest(company, request, updatedBy);
        companyRepository.save(company);
        
        eventPublisher.publishCompanyUpdated(eventMapper.toUpdatedEvent(company));
    }
    
    @Transactional
    @CacheEvict(value = {"companies", "companiesList"}, allEntries = true)
    public void deleteCompany(UUID companyId, UUID tenantId, String deletedBy) {
        log.info("Deleting company: {}", companyId);
        
        Company company = getCompanyEntity(companyId, tenantId);
        company.markAsDeleted();
        company.setStatus(CompanyStatus.DELETED);
        company.setActive(false);
        companyRepository.save(company);
        
        eventPublisher.publishCompanyDeleted(eventMapper.toDeletedEvent(company));
    }
    
    @Transactional
    @CacheEvict(value = {"companies", "companiesList"}, allEntries = true)
    public void updateSettings(UUID companyId, UpdateCompanySettingsRequest request, UUID tenantId, String updatedBy) {
        log.info("Updating settings for company: {}", companyId);
        
        Company company = getCompanyEntity(companyId, tenantId);
        
        Map<String, Object> allSettings = new java.util.HashMap<>();
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
        
        company.setSettings(allSettings);
        company.setUpdatedBy(updatedBy);
        companyRepository.save(company);
        
        eventPublisher.publishCompanyUpdated(eventMapper.toUpdatedEvent(company));
    }
    
    @Transactional
    @CacheEvict(value = {"companies", "companiesList"}, allEntries = true)
    public void updateSubscription(UUID companyId, UpdateSubscriptionRequest request, UUID tenantId, String updatedBy) {
        log.info("Updating subscription for company: {}", companyId);
        
        Company company = getCompanyEntity(companyId, tenantId);
        company.setSubscriptionPlan(request.getPlan());
        company.setMaxUsers(request.getMaxUsers());
        company.setSubscriptionEndDate(request.getEndDate());
        company.setUpdatedBy(updatedBy);
        companyRepository.save(company);
        
        eventPublisher.publishCompanyUpdated(eventMapper.toUpdatedEvent(company));
    }
    
    @Transactional
    @CacheEvict(value = {"companies", "companiesList"}, allEntries = true)
    public void activateCompany(UUID companyId, UUID tenantId, String updatedBy) {
        log.info("Activating company: {}", companyId);
        
        Company company = getCompanyEntity(companyId, tenantId);
        company.setStatus(CompanyStatus.ACTIVE);
        company.setActive(true);
        company.setUpdatedBy(updatedBy);
        companyRepository.save(company);
        
        eventPublisher.publishCompanyUpdated(eventMapper.toUpdatedEvent(company));
    }
    
    @Transactional
    @CacheEvict(value = {"companies", "companiesList"}, allEntries = true)
    public void deactivateCompany(UUID companyId, UUID tenantId, String updatedBy) {
        log.info("Deactivating company: {}", companyId);
        
        Company company = getCompanyEntity(companyId, tenantId);
        company.setStatus(CompanyStatus.INACTIVE);
        company.setActive(false);
        company.setUpdatedBy(updatedBy);
        companyRepository.save(company);
        
        eventPublisher.publishCompanyUpdated(eventMapper.toUpdatedEvent(company));
    }
    
    @Cacheable(value = "companies", key = "#companyId")
    public CompanyResponse getCompany(UUID companyId, UUID tenantId) {
        log.debug("Getting company: {}", companyId);
        
        Company company = getCompanyEntity(companyId, tenantId);
        return companyMapper.toResponse(company);
    }
    
    @Cacheable(value = "companiesList", key = "#tenantId")
    public List<CompanyResponse> listCompanies(UUID tenantId) {
        log.debug("Listing companies for tenant: {}", tenantId);
        
        return companyRepository.findByTenantId(tenantId).stream()
                .filter(c -> !c.isDeleted())
                .map(companyMapper::toResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * List companies with pagination
     * 
     * @param tenantId Tenant ID
     * @param pageable Pagination parameters (page, size, sort)
     * @return Paginated company list
     */
    public PagedResponse<CompanyResponse> listCompaniesPaginated(UUID tenantId, Pageable pageable) {
        log.debug("Listing companies for tenant: {} (page: {}, size: {})", 
                  tenantId, pageable.getPageNumber(), pageable.getPageSize());
        
        Page<Company> companyPage = companyRepository.findByTenantIdAndDeletedFalse(tenantId, pageable);
        
        return PagedResponse.fromPage(companyPage, companyMapper::toResponse);
    }
    
    public List<CompanyResponse> searchCompanies(UUID tenantId, String searchTerm) {
        log.debug("Searching companies with term: {} for tenant: {}", searchTerm, tenantId);
        
        return companyRepository.searchByNameAndTenantId(searchTerm, tenantId).stream()
                .map(companyMapper::toResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Search companies with pagination
     * 
     * @param tenantId Tenant ID
     * @param searchTerm Search keyword
     * @param pageable Pagination parameters
     * @return Paginated search results
     */
    public PagedResponse<CompanyResponse> searchCompaniesPaginated(UUID tenantId, String searchTerm, Pageable pageable) {
        log.debug("Searching companies with term: {} for tenant: {} (page: {}, size: {})", 
                  searchTerm, tenantId, pageable.getPageNumber(), pageable.getPageSize());
        
        Page<Company> companyPage = companyRepository.searchByNameAndTenantIdPaginated(searchTerm, tenantId, pageable);
        
        return PagedResponse.fromPage(companyPage, companyMapper::toResponse);
    }
    
    public List<CompanyResponse> getCompaniesByStatus(CompanyStatus status, UUID tenantId) {
        log.debug("Getting companies with status: {} for tenant: {}", status, tenantId);
        
        return companyRepository.findByStatusAndTenantId(status, tenantId).stream()
                .map(companyMapper::toResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Get companies by status with pagination
     * 
     * @param status Company status filter
     * @param tenantId Tenant ID
     * @param pageable Pagination parameters
     * @return Paginated status-filtered results
     */
    public PagedResponse<CompanyResponse> getCompaniesByStatusPaginated(CompanyStatus status, UUID tenantId, Pageable pageable) {
        log.debug("Getting companies with status: {} for tenant: {} (page: {}, size: {})", 
                  status, tenantId, pageable.getPageNumber(), pageable.getPageSize());
        
        Page<Company> companyPage = companyRepository.findByStatusAndTenantId(status, tenantId, pageable);
        
        return PagedResponse.fromPage(companyPage, companyMapper::toResponse);
    }
    
    public CheckDuplicateResponse checkDuplicate(CheckDuplicateRequest request, UUID tenantId) {
        log.info("Checking duplicate for company - Name: {}, TaxId: {}, RegNumber: {}", 
            request.getName(), request.getTaxId(), request.getRegistrationNumber());
        
        // Priority 1: Check exact tax ID match (CRITICAL - legal identifier)
        if (request.getTaxId() != null && !request.getTaxId().isBlank()) {
            Optional<Company> taxIdMatch = companyRepository.findByTaxIdAndTenantId(request.getTaxId(), tenantId);
            if (taxIdMatch.isPresent()) {
                Company matched = taxIdMatch.get();
                return CheckDuplicateResponse.builder()
                        .isDuplicate(true)
                        .matchType("TAX_ID")
                        .matchedCompanyId(matched.getId().toString())
                        .matchedCompanyName(matched.getName().getValue())
                        .matchedTaxId(matched.getTaxId())
                        .confidence(1.0)
                        .message("Tax ID is already registered")
                        .recommendation("If this is your company, please use the forgot password option")
                        .build();
            }
        }
        
        // Priority 2: Check exact registration number match (CRITICAL - legal identifier)
        if (request.getRegistrationNumber() != null && !request.getRegistrationNumber().isBlank()) {
            Optional<Company> regNumberMatch = companyRepository.findByRegistrationNumberAndTenantId(
                request.getRegistrationNumber(), tenantId);
            if (regNumberMatch.isPresent()) {
                Company matched = regNumberMatch.get();
                return CheckDuplicateResponse.builder()
                        .isDuplicate(true)
                        .matchType("REGISTRATION_NUMBER")
                        .matchedCompanyId(matched.getId().toString())
                        .matchedCompanyName(matched.getName().getValue())
                        .matchedTaxId(matched.getTaxId())
                        .confidence(1.0)
                        .message("Registration number is already registered")
                        .recommendation("If this is your company, please use the forgot password option")
                        .build();
            }
        }
        
        // Priority 3: Check exact name match
        if (companyRepository.existsByNameAndTenantId(request.getName(), tenantId)) {
            // Find the actual company for details
            List<Company> nameMatches = companyRepository.searchByNameAndTenantId(request.getName(), tenantId);
            if (!nameMatches.isEmpty()) {
                Company matched = nameMatches.get(0);
                return CheckDuplicateResponse.builder()
                        .isDuplicate(true)
                        .matchType("NAME_EXACT")
                        .matchedCompanyId(matched.getId().toString())
                        .matchedCompanyName(matched.getName().getValue())
                        .matchedTaxId(matched.getTaxId())
                        .confidence(1.0)
                        .message("Company name is already registered")
                        .recommendation("If this is your company, please use the forgot password option")
                        .build();
            }
        }
        
        // Priority 4: Check fuzzy name similarity (WARNING - not blocking)
        if (request.getName() != null && request.getName().trim().length() >= duplicateConfig.getFuzzySearchMinLength()) {
            List<Company> nameSimilar = companyRepository.findSimilarCompanies(
                    tenantId, 
                    request.getName(), 
                    duplicateConfig.getNameSimilarityThreshold()
            );
            
            if (!nameSimilar.isEmpty()) {
                Company matched = nameSimilar.get(0);
                return CheckDuplicateResponse.builder()
                        .isDuplicate(true)
                        .matchType("NAME_SIMILAR")
                        .matchedCompanyId(matched.getId().toString())
                        .matchedCompanyName(matched.getName().getValue())
                        .matchedTaxId(matched.getTaxId())
                        .confidence(0.85)
                        .message("A similar company name was found: " + matched.getName().getValue())
                        .recommendation("Please verify this is not a duplicate or a typo")
                        .build();
            }
        }
        
        return CheckDuplicateResponse.noDuplicate();
    }
    
    /**
     * Check for duplicate companies GLOBALLY (across all tenants)
     * Used during tenant onboarding to prevent cross-tenant duplicates
     * 
     * Multi-level validation (priority-based):
     * 1. Tax ID (GLOBAL) → BLOCK - Globally unique legal identifier
     * 2. Registration Number (GLOBAL) → BLOCK - Globally unique legal identifier
     * 3. Legal Name + Country (EXACT) → BLOCK - Cannot have same legal name in same country
     * 4. Legal Name + Country (TOKEN-BASED) → BLOCK if typo - Smart fuzzy matching
     * 5. Company Name (NORMALIZED) → BLOCK - Marketing name exact match
     * 6. Company Name (TOKEN-BASED) → WARN - Allow different companies with generic terms
     * 
     * SMART TOKEN-BASED MATCHING:
     * - Filters common words: "tekstil", "limited", "sanayi", etc.
     * - Compares unique tokens only
     * - "Akme Tekstil" vs "Akkayalar Tekstil" → ALLOW (different unique tokens)
     * - "Acme Tekstil" vs "Acmee Tekstil" → BLOCK (typo in unique token)
     * 
     * GLOBAL NORMALIZATION:
     * - ICU4J transliteration (İ→i, ü→u, München→munchen)
     * - Multi-language suffix removal (A.Ş., GmbH, Inc., SA, SAS, SpA)
     * - Apache Commons Text (Jaccard, Jaro-Winkler)
     */
    public CheckDuplicateResponse checkDuplicateGlobal(CheckDuplicateRequest request) {
        log.info("Checking GLOBAL duplicate for company - Name: {}, Legal: {}, Country: {}, TaxId: {}, RegNumber: {}", 
            request.getName(), request.getLegalName(), request.getCountry(), 
            request.getTaxId(), request.getRegistrationNumber());
        
        // Priority 1: Check exact tax ID match GLOBALLY (CRITICAL - legal identifier)
        if (request.getTaxId() != null && !request.getTaxId().isBlank()) {
            Optional<Company> taxIdMatch = companyRepository.findByTaxIdGlobal(request.getTaxId());
            if (taxIdMatch.isPresent()) {
                Company matched = taxIdMatch.get();
                return CheckDuplicateResponse.builder()
                        .isDuplicate(true)
                        .matchType("TAX_ID")
                        .matchedCompanyId(matched.getId().toString())
                        .matchedCompanyName(matched.getName().getValue())
                        .matchedTaxId(matched.getTaxId())
                        .confidence(1.0)
                        .message("Tax ID is already registered")
                        .recommendation("If this is your company, please use the forgot password option")
                        .build();
            }
        }
        
        // Priority 2: Check exact registration number match GLOBALLY (CRITICAL)
        if (request.getRegistrationNumber() != null && !request.getRegistrationNumber().isBlank()) {
            Optional<Company> regNumberMatch = companyRepository.findByRegistrationNumberGlobal(
                request.getRegistrationNumber());
            if (regNumberMatch.isPresent()) {
                Company matched = regNumberMatch.get();
                return CheckDuplicateResponse.builder()
                        .isDuplicate(true)
                        .matchType("REGISTRATION_NUMBER")
                        .matchedCompanyId(matched.getId().toString())
                        .matchedCompanyName(matched.getName().getValue())
                        .matchedTaxId(matched.getTaxId())
                        .confidence(1.0)
                        .message("Registration number is already registered")
                        .recommendation("If this is your company, please use the forgot password option")
                        .build();
            }
        }
        
        // Priority 3: Check LEGAL NAME + COUNTRY (CRITICAL - same legal name in same country is ILLEGAL!)
        if (request.getLegalName() != null && !request.getLegalName().isBlank() 
            && request.getCountry() != null && !request.getCountry().isBlank()) {
            
            Optional<Company> legalNameMatch = companyRepository.findByLegalNameAndCountry(
                request.getLegalName(), request.getCountry());
            
            if (legalNameMatch.isPresent()) {
                Company matched = legalNameMatch.get();
                return CheckDuplicateResponse.builder()
                        .isDuplicate(true)
                        .matchType("LEGAL_NAME_COUNTRY")
                        .matchedCompanyId(matched.getId().toString())
                        .matchedCompanyName(matched.getName().getValue())
                        .matchedTaxId(matched.getTaxId())
                        .confidence(1.0)
                        .message(String.format(
                            "A company with this legal name is already registered in %s. Legal name: '%s'",
                            request.getCountry(), matched.getLegalName()
                        ))
                        .recommendation("If this is your company, please use the forgot password option")
                        .build();
            }
            
            // Priority 4: Check LEGAL NAME fuzzy match (SAME COUNTRY ONLY - typo detection)
            // Get companies in same country
            List<Company> companiesInSameCountry = getCompaniesByCountry(request.getCountry());
            
            for (Company company : companiesInSameCountry) {
                if (company.getLegalName() == null || company.getLegalName().isBlank()) {
                    continue;
                }
                
                // Token-based fuzzy match on LEGAL NAME
                TokenBasedSimilarityUtil.TokenSimilarityResult tokenResult = 
                    tokenSimilarityUtil.calculateTokenSimilarity(
                        request.getLegalName(), 
                        company.getLegalName()
                    );
                
                log.debug("Legal name token similarity for '{}' vs '{}' (same country: {}): {}", 
                    request.getLegalName(), company.getLegalName(), 
                    request.getCountry(), tokenResult.getExplanation());
                
                // STRICT on legal name - block if duplicate detected
                if (tokenResult.isDuplicate()) {
                    return CheckDuplicateResponse.builder()
                            .isDuplicate(true)
                            .matchType("LEGAL_NAME_TOKEN_DUPLICATE")
                            .matchedCompanyId(company.getId().toString())
                            .matchedCompanyName(company.getName().getValue())
                            .matchedTaxId(company.getTaxId())
                            .confidence(tokenResult.getConfidence())
                            .message(String.format(
                                "Very similar LEGAL name found in %s: '%s' (%.0f%% confidence). This appears to be a duplicate or typo.",
                                request.getCountry(),
                                company.getLegalName(), 
                                tokenResult.getConfidence() * 100
                            ))
                            .recommendation("If this is your company, please use the forgot password option. Legal names must be unique within the same country.")
                            .build();
                }
            }
        }
        
        // Priority 5: Check normalized COMPANY NAME match GLOBALLY (EXACT only)
        // This catches "A.Ş." vs "AS", "İstanbul" vs "Istanbul", etc.
        // NOTE: We do NOT apply fuzzy matching to company name (only legal name)
        // Reason: Company names are marketing/brand names and can be similar
        // Example: "Akkayalar Tekstil" and "Akme Tekstil" → ALLOWED
        if (request.getName() != null && !request.getName().isBlank()) {
            String normalizedRequestName = normalizationUtil.normalizeForComparison(request.getName());
            
            // Get all companies and check normalized names
            // TODO: Optimize with database-level normalization in future
            List<Company> allCompanies = companyRepository.findAll().stream()
                    .filter(c -> !c.isDeleted())
                    .collect(Collectors.toList());
            
            for (Company company : allCompanies) {
                String normalizedExistingName = normalizationUtil.normalizeForComparison(
                    company.getName().getValue());
                
                // Exact match after normalization
                if (normalizedRequestName.equals(normalizedExistingName)) {
                    return CheckDuplicateResponse.builder()
                            .isDuplicate(true)
                            .matchType("NAME_NORMALIZED_EXACT")
                            .matchedCompanyId(company.getId().toString())
                            .matchedCompanyName(company.getName().getValue())
                            .matchedTaxId(company.getTaxId())
                            .confidence(1.0)
                            .message("Company name is already registered (normalized match)")
                            .recommendation("If this is your company, please use the forgot password option")
                            .build();
                }
            }
        }
        
        // No duplicates found
        return CheckDuplicateResponse.noDuplicate();
    }
    
    public CompanyAutocompleteResponse autocomplete(String query, UUID tenantId) {
        log.debug("Autocomplete search: {}", query);
        
        if (query == null || query.trim().length() < duplicateConfig.getAutocompleteMinLength()) {
            return CompanyAutocompleteResponse.builder()
                    .suggestions(List.of())
                    .totalCount(0)
                    .build();
        }
        
        List<Company> companies = companyRepository.searchCompaniesForAutocomplete(
                tenantId, 
                query, 
                duplicateConfig.getAutocompleteMaxResults()
        );
        
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
     * Find companies with similar names using PostgreSQL trigram similarity
     * 
     * @param name Company name to search for
     * @param threshold Similarity threshold (0.0 to 1.0), lower = more results
     * @param tenantId Tenant ID for multi-tenancy
     * @return Response with list of similar companies and their similarity scores
     */
    public CompanySimilarResponse findSimilar(String name, Double threshold, UUID tenantId) {
        log.debug("Finding similar companies for name: {} with threshold: {}", name, threshold);
        
        // Use configured threshold if not provided
        double searchThreshold = threshold != null ? threshold : duplicateConfig.getDatabaseSearchThreshold();
        
        // Validate threshold range
        if (searchThreshold < 0.0 || searchThreshold > 1.0) {
            searchThreshold = 0.3; // default safe value
        }
        
        // Minimum name length check
        if (name == null || name.trim().length() < duplicateConfig.getFuzzySearchMinLength()) {
            return CompanySimilarResponse.builder()
                    .matches(List.of())
                    .totalCount(0)
                    .threshold(searchThreshold)
                    .build();
        }
        
        List<Company> similarCompanies = companyRepository.findSimilarCompanies(
                tenantId, 
                name.trim(), 
                searchThreshold
        );
        
        // Map to response with similarity scores
        List<CompanySimilarResponse.SimilarCompany> matches = similarCompanies.stream()
                .map(company -> CompanySimilarResponse.SimilarCompany.builder()
                        .id(company.getId().toString())
                        .name(company.getName().getValue())
                        .legalName(company.getLegalName())
                        .taxId(company.getTaxId())
                        .type(company.getType().name())
                        .industry(company.getIndustry().name())
                        .similarityScore(0.85) // Placeholder - PostgreSQL returns ordered results
                        .build())
                .toList();
        
        return CompanySimilarResponse.builder()
                .matches(matches)
                .totalCount(matches.size())
                .threshold(searchThreshold)
                .build();
    }
    
    private Company getCompanyEntity(UUID companyId, UUID tenantId) {
        return companyRepository.findByIdAndTenantId(companyId, tenantId)
                .filter(c -> !c.isDeleted())
                .orElseThrow(() -> new CompanyNotFoundException(companyId));
    }
    
    /**
     * Gets companies in a specific country
     * 
     * Uses Contact Service to get country from addresses
     * Falls back to Company.country field if Contact Service unavailable
     */
    private List<Company> getCompaniesByCountry(String country) {
        log.debug("Getting companies in country: {}", country);
        
        // Strategy: Use Company.country field (denormalized for performance)
        // This field is populated during company creation from address data
        List<Company> companies = companyRepository.findAll().stream()
                .filter(c -> !c.isDeleted())
                .filter(c -> c.getCountry() != null && country.equalsIgnoreCase(c.getCountry()))
                .collect(Collectors.toList());
        
        log.debug("Found {} companies in country: {}", companies.size(), country);
        return companies;
    }
}
