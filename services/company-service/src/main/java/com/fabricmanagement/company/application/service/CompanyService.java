package com.fabricmanagement.company.application.service;

import com.fabricmanagement.company.api.dto.request.CreateCompanyRequest;
import com.fabricmanagement.company.api.dto.request.UpdateCompanyRequest;
import com.fabricmanagement.company.api.dto.request.UpdateCompanySettingsRequest;
import com.fabricmanagement.company.api.dto.request.UpdateSubscriptionRequest;
import com.fabricmanagement.company.api.dto.request.CheckDuplicateRequest;
import com.fabricmanagement.company.api.dto.response.CompanyResponse;
import com.fabricmanagement.company.api.dto.response.CheckDuplicateResponse;
import com.fabricmanagement.company.api.dto.response.CompanyAutocompleteResponse;
import com.fabricmanagement.company.application.mapper.CompanyMapper;
import com.fabricmanagement.company.application.mapper.CompanyEventMapper;
import com.fabricmanagement.company.domain.aggregate.Company;
import com.fabricmanagement.company.domain.exception.CompanyAlreadyExistsException;
import com.fabricmanagement.company.domain.exception.CompanyNotFoundException;
import com.fabricmanagement.company.domain.valueobject.CompanyStatus;
import com.fabricmanagement.company.infrastructure.repository.CompanyRepository;
import com.fabricmanagement.company.infrastructure.config.DuplicateDetectionConfig;
import com.fabricmanagement.company.infrastructure.messaging.CompanyEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
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
    
    @Transactional
    @CacheEvict(value = {"companies", "companiesList"}, allEntries = true)
    public UUID createCompany(CreateCompanyRequest request, UUID tenantId, String createdBy) {
        log.info("Creating company: {} for tenant: {}", request.getName(), tenantId);
        
        if (companyRepository.existsByNameAndTenantId(request.getName(), tenantId)) {
            throw new CompanyAlreadyExistsException(request.getName());
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
    
    public List<CompanyResponse> searchCompanies(UUID tenantId, String searchTerm) {
        log.debug("Searching companies with term: {} for tenant: {}", searchTerm, tenantId);
        
        return companyRepository.searchByNameAndTenantId(searchTerm, tenantId).stream()
                .map(companyMapper::toResponse)
                .collect(Collectors.toList());
    }
    
    public List<CompanyResponse> getCompaniesByStatus(CompanyStatus status, UUID tenantId) {
        log.debug("Getting companies with status: {} for tenant: {}", status, tenantId);
        
        return companyRepository.findByStatusAndTenantId(status, tenantId).stream()
                .map(companyMapper::toResponse)
                .collect(Collectors.toList());
    }
    
    public CheckDuplicateResponse checkDuplicate(CheckDuplicateRequest request, UUID tenantId) {
        log.info("Checking duplicate for company: {}", request.getName());
        
        List<Company> potentialDuplicates = companyRepository.findPotentialDuplicates(
                tenantId, 
                request.getTaxId(), 
                request.getRegistrationNumber()
        );
        
        if (request.getName() != null && request.getName().trim().length() >= duplicateConfig.getFuzzySearchMinLength()) {
            List<Company> nameSimilar = companyRepository.findSimilarCompanies(
                    tenantId, 
                    request.getName(), 
                    duplicateConfig.getDatabaseSearchThreshold()
            );
            
            for (var company : nameSimilar) {
                if (potentialDuplicates.stream().noneMatch(c -> c.getId().equals(company.getId()))) {
                    potentialDuplicates.add(company);
                }
            }
        }
        
        if (potentialDuplicates.isEmpty()) {
            return CheckDuplicateResponse.noDuplicate();
        }
        
        Company matched = potentialDuplicates.get(0);
        return CheckDuplicateResponse.builder()
                .isDuplicate(true)
                .matchType("SIMILAR")
                .matchedCompanyId(matched.getId().toString())
                .matchedCompanyName(matched.getName().getValue())
                .confidence(0.8)
                .message("Similar company found")
                .recommendation("Please verify this is not a duplicate")
                .build();
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
    
    private Company getCompanyEntity(UUID companyId, UUID tenantId) {
        return companyRepository.findByIdAndTenantId(companyId, tenantId)
                .filter(c -> !c.isDeleted())
                .orElseThrow(() -> new CompanyNotFoundException(companyId));
    }
}
