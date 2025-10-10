package com.fabricmanagement.company.api;

import com.fabricmanagement.company.application.dto.*;
import com.fabricmanagement.company.application.service.CompanyService;
import com.fabricmanagement.company.domain.valueobject.CompanyStatus;
import com.fabricmanagement.shared.application.context.SecurityContext;
import com.fabricmanagement.shared.application.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Company REST Controller
 * 
 * Provides API endpoints for company management.
 * Follows Clean Architecture principles - only handles HTTP concerns.
 * 
 * Uses Spring Security's @AuthenticationPrincipal - 100% framework-native!
 * 
 * API Version: v1
 * Base Path: /api/v1/companies
 */
@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
@Slf4j
public class CompanyController {
    
    private final CompanyService companyService;
    private final com.fabricmanagement.company.infrastructure.config.DuplicateDetectionConfig duplicateConfig;
    
    /**
     * Creates a new company
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPANY_MANAGER')")
    public ResponseEntity<ApiResponse<UUID>> createCompany(
            @Valid @RequestBody CreateCompanyRequest request,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.info("Creating company: {}", request.getName());
        
        UUID companyId = companyService.createCompany(request, ctx.getTenantId(), ctx.getUserId());
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(companyId, "Company created successfully"));
    }
    
    /**
     * Gets a company by ID
     */
    @GetMapping("/{companyId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CompanyResponse>> getCompany(
            @PathVariable UUID companyId,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.debug("Getting company: {}", companyId);
        
        CompanyResponse company = companyService.getCompany(companyId, ctx.getTenantId());
        
        return ResponseEntity.ok(ApiResponse.success(company));
    }
    
    /**
     * Lists all companies for the current tenant
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<CompanyResponse>>> listCompanies(
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.debug("Listing companies");
        
        List<CompanyResponse> companies = companyService.listCompanies(ctx.getTenantId());
        
        return ResponseEntity.ok(ApiResponse.success(companies));
    }
    
    /**
     * Updates a company
     */
    @PutMapping("/{companyId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPANY_MANAGER')")
    public ResponseEntity<ApiResponse<Void>> updateCompany(
            @PathVariable UUID companyId,
            @Valid @RequestBody UpdateCompanyRequest request,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.info("Updating company: {}", companyId);
        
        companyService.updateCompany(companyId, request, ctx.getTenantId(), ctx.getUserId());
        
        return ResponseEntity.ok(ApiResponse.success(null, "Company updated successfully"));
    }
    
    /**
     * Deletes a company (soft delete)
     */
    @DeleteMapping("/{companyId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteCompany(
            @PathVariable UUID companyId,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.info("Deleting company: {}", companyId);
        
        companyService.deleteCompany(companyId, ctx.getTenantId(), ctx.getUserId());
        
        return ResponseEntity.ok(ApiResponse.success(null, "Company deleted successfully"));
    }
    
    /**
     * Activates a company
     */
    @PostMapping("/{companyId}/activate")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> activateCompany(
            @PathVariable UUID companyId,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.info("Activating company: {}", companyId);
        
        companyService.activateCompany(companyId, ctx.getTenantId(), ctx.getUserId());
        
        return ResponseEntity.ok(ApiResponse.success(null, "Company activated successfully"));
    }
    
    /**
     * Deactivates a company
     */
    @PostMapping("/{companyId}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivateCompany(
            @PathVariable UUID companyId,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.info("Deactivating company: {}", companyId);
        
        companyService.deactivateCompany(companyId, ctx.getTenantId(), ctx.getUserId());
        
        return ResponseEntity.ok(ApiResponse.success(null, "Company deactivated successfully"));
    }
    
    /**
     * Gets companies by status
     */
    @GetMapping("/status/{status}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<CompanyResponse>>> getCompaniesByStatus(
            @PathVariable CompanyStatus status,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.debug("Getting companies with status: {}", status);
        
        List<CompanyResponse> companies = companyService.getCompaniesByStatus(status, ctx.getTenantId());
        
        return ResponseEntity.ok(ApiResponse.success(companies));
    }
    
    /**
     * Searches companies by criteria
     */
    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<CompanyResponse>>> searchCompanies(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) String companyType,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.debug("Searching companies with criteria: name={}, industry={}, companyType={}",
                name, industry, companyType);
        
        List<CompanyResponse> companies = companyService.searchCompanies(ctx.getTenantId(), name, industry, companyType);
        
        return ResponseEntity.ok(ApiResponse.success(companies));
    }
    
    /**
     * Updates company settings
     */
    @PutMapping("/{companyId}/settings")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPANY_MANAGER')")
    public ResponseEntity<ApiResponse<Void>> updateCompanySettings(
            @PathVariable UUID companyId,
            @Valid @RequestBody UpdateCompanySettingsRequest request,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.info("Updating company settings: {}", companyId);
        
        companyService.updateCompanySettings(companyId, request, ctx.getTenantId(), ctx.getUserId());
        
        return ResponseEntity.ok(ApiResponse.success(null, "Company settings updated successfully"));
    }
    
    /**
     * Updates company subscription
     */
    @PutMapping("/{companyId}/subscription")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> updateSubscription(
            @PathVariable UUID companyId,
            @Valid @RequestBody UpdateSubscriptionRequest request,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.info("Updating company subscription: {}", companyId);
        
        companyService.updateSubscription(companyId, request, ctx.getTenantId(), ctx.getUserId());
        
        return ResponseEntity.ok(ApiResponse.success(null, "Company subscription updated successfully"));
    }
    
    // =========================================================================
    // DUPLICATE PREVENTION ENDPOINTS
    // =========================================================================
    
    /**
     * Check for duplicate companies before creating
     * 
     * This endpoint helps prevent duplicate company creation by:
     * 1. Checking exact match on Tax ID and Registration Number
     * 2. Fuzzy matching on company names (detects typos and similar names)
     * 3. Returns confidence score and matched company details
     * 
     * Usage: Call this endpoint before creating a company (frontend validation)
     * 
     * @param request Company details to check
     * @param ctx Security context
     * @return Duplicate check result with matched company (if any)
     */
    @PostMapping("/check-duplicate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CheckDuplicateResponse>> checkDuplicate(
            @Valid @RequestBody CheckDuplicateRequest request,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.info("Checking for duplicate company: {}", request.getName());
        
        CheckDuplicateResponse result = companyService.checkDuplicate(request, ctx.getTenantId());
        
        if (result.isDuplicate()) {
            log.warn("Potential duplicate found: {} (confidence: {}%)", 
                result.getMatchedCompanyName(), (int)(result.getConfidence() * 100));
        }
        
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    /**
     * Search companies for autocomplete (search-as-you-type)
     * 
     * This endpoint provides fast search results for frontend autocomplete:
     * - Returns top 10 matches
     * - Uses full-text search for performance
     * - Helps users find existing companies before creating duplicates
     * 
     * Usage: Call this endpoint as user types in company name field
     * 
     * @param query Search term (partial company name)
     * @param ctx Security context
     * @return List of matching companies
     */
    @GetMapping("/autocomplete")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CompanyAutocompleteResponse>> autocomplete(
            @RequestParam("q") String query,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.debug("Autocomplete search: {}", query);
        
        // Configuration-driven validation (NO MAGIC NUMBERS!)
        int minLength = duplicateConfig.getAutocompleteMinLength();
        if (query == null || query.trim().length() < minLength) {
            return ResponseEntity.ok(ApiResponse.success(
                CompanyAutocompleteResponse.builder()
                    .suggestions(List.of())
                    .totalCount(0)
                    .build(),
                String.format("Query too short (minimum %d characters)", minLength)
            ));
        }
        
        CompanyAutocompleteResponse result = companyService.autocomplete(query.trim(), ctx.getTenantId());
        
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    /**
     * Find similar companies by name
     * 
     * This endpoint finds companies with similar names using fuzzy matching:
     * - Detects typos (ABC vs ABD)
     * - Handles abbreviations (Ltd vs Limited)
     * - Returns similarity scores
     * 
     * Usage: Advanced duplicate detection, data quality checks
     * 
     * @param name Company name to search for
     * @param threshold Minimum similarity (0.0-1.0), default 0.3
     * @param ctx Security context
     * @return List of similar companies with similarity scores
     */
    @GetMapping("/similar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<CompanyResponse>>> findSimilar(
            @RequestParam("name") String name,
            @RequestParam(value = "threshold", defaultValue = "0.3") double threshold,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.debug("Finding similar companies to: {}", name);
        
        // Configuration-driven validation (NO MAGIC NUMBERS!)
        int minLength = duplicateConfig.getFuzzySearchMinLength();
        if (name == null || name.trim().length() < minLength) {
            return ResponseEntity.ok(ApiResponse.success(
                List.of(),
                String.format("Query too short (minimum %d characters)", minLength)
            ));
        }
        
        // Validate threshold range
        if (threshold < 0.0 || threshold > 1.0) {
            threshold = duplicateConfig.getDatabaseSearchThreshold(); // Use configured default
        }
        
        List<CompanyResponse> similar = companyService.findSimilar(name.trim(), threshold, ctx.getTenantId());
        
        return ResponseEntity.ok(ApiResponse.success(similar));
    }
}
