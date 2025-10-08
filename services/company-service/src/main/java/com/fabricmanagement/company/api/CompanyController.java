package com.fabricmanagement.company.api;

import com.fabricmanagement.company.application.dto.*;
import com.fabricmanagement.company.application.service.CompanyService;
import com.fabricmanagement.company.domain.valueobject.CompanyStatus;
import com.fabricmanagement.shared.application.response.ApiResponse;
import com.fabricmanagement.shared.infrastructure.security.SecurityContextHolder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Company REST Controller
 * 
 * Provides API endpoints for company management.
 * Follows Clean Architecture principles - only handles HTTP concerns.
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
    
    /**
     * Creates a new company
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPANY_MANAGER')")
    public ResponseEntity<ApiResponse<UUID>> createCompany(@Valid @RequestBody CreateCompanyRequest request) {
        log.info("Creating company: {}", request.getName());
        
        UUID tenantId = SecurityContextHolder.getCurrentTenantId();
        String createdBy = SecurityContextHolder.getCurrentUserId();
        
        UUID companyId = companyService.createCompany(request, tenantId, createdBy);
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(companyId, "Company created successfully"));
    }
    
    /**
     * Gets a company by ID
     */
    @GetMapping("/{companyId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CompanyResponse>> getCompany(@PathVariable UUID companyId) {
        log.debug("Getting company: {}", companyId);
        
        UUID tenantId = SecurityContextHolder.getCurrentTenantId();
        CompanyResponse company = companyService.getCompany(companyId, tenantId);
        
        return ResponseEntity.ok(ApiResponse.success(company));
    }
    
    /**
     * Lists all companies for the current tenant
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<CompanyResponse>>> listCompanies() {
        log.debug("Listing companies");
        
        UUID tenantId = SecurityContextHolder.getCurrentTenantId();
        List<CompanyResponse> companies = companyService.listCompanies(tenantId);
        
        return ResponseEntity.ok(ApiResponse.success(companies));
    }
    
    /**
     * Updates a company
     */
    @PutMapping("/{companyId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPANY_MANAGER')")
    public ResponseEntity<ApiResponse<Void>> updateCompany(
            @PathVariable UUID companyId,
            @Valid @RequestBody UpdateCompanyRequest request) {
        
        log.info("Updating company: {}", companyId);
        
        UUID tenantId = SecurityContextHolder.getCurrentTenantId();
        String updatedBy = SecurityContextHolder.getCurrentUserId();
        
        companyService.updateCompany(companyId, request, tenantId, updatedBy);
        
        return ResponseEntity.ok(ApiResponse.success(null, "Company updated successfully"));
    }
    
    /**
     * Deletes a company (soft delete)
     */
    @DeleteMapping("/{companyId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteCompany(@PathVariable UUID companyId) {
        log.info("Deleting company: {}", companyId);
        
        UUID tenantId = SecurityContextHolder.getCurrentTenantId();
        String deletedBy = SecurityContextHolder.getCurrentUserId();
        
        companyService.deleteCompany(companyId, tenantId, deletedBy);
        
        return ResponseEntity.ok(ApiResponse.success(null, "Company deleted successfully"));
    }
    
    /**
     * Activates a company
     */
    @PostMapping("/{companyId}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> activateCompany(@PathVariable UUID companyId) {
        log.info("Activating company: {}", companyId);
        
        UUID tenantId = SecurityContextHolder.getCurrentTenantId();
        String updatedBy = SecurityContextHolder.getCurrentUserId();
        
        companyService.activateCompany(companyId, tenantId, updatedBy);
        
        return ResponseEntity.ok(ApiResponse.success(null, "Company activated successfully"));
    }
    
    /**
     * Deactivates a company
     */
    @PostMapping("/{companyId}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivateCompany(@PathVariable UUID companyId) {
        log.info("Deactivating company: {}", companyId);
        
        UUID tenantId = SecurityContextHolder.getCurrentTenantId();
        String updatedBy = SecurityContextHolder.getCurrentUserId();
        
        companyService.deactivateCompany(companyId, tenantId, updatedBy);
        
        return ResponseEntity.ok(ApiResponse.success(null, "Company deactivated successfully"));
    }
    
    /**
     * Gets companies by status
     */
    @GetMapping("/status/{status}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<CompanyResponse>>> getCompaniesByStatus(
            @PathVariable CompanyStatus status) {
        
        log.debug("Getting companies with status: {}", status);
        
        UUID tenantId = SecurityContextHolder.getCurrentTenantId();
        List<CompanyResponse> companies = companyService.getCompaniesByStatus(status, tenantId);
        
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
            @RequestParam(required = false) String companyType) {
        
        log.debug("Searching companies with criteria: name={}, industry={}, companyType={}",
                name, industry, companyType);
        
        UUID tenantId = SecurityContextHolder.getCurrentTenantId();
        List<CompanyResponse> companies = companyService.searchCompanies(tenantId, name, industry, companyType);
        
        return ResponseEntity.ok(ApiResponse.success(companies));
    }
    
    /**
     * Updates company settings
     */
    @PutMapping("/{companyId}/settings")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPANY_MANAGER')")
    public ResponseEntity<ApiResponse<Void>> updateCompanySettings(
            @PathVariable UUID companyId,
            @Valid @RequestBody UpdateCompanySettingsRequest request) {
        
        log.info("Updating company settings: {}", companyId);
        
        UUID tenantId = SecurityContextHolder.getCurrentTenantId();
        String updatedBy = SecurityContextHolder.getCurrentUserId();
        
        companyService.updateCompanySettings(companyId, request, tenantId, updatedBy);
        
        return ResponseEntity.ok(ApiResponse.success(null, "Company settings updated successfully"));
    }
    
    /**
     * Updates company subscription
     */
    @PutMapping("/{companyId}/subscription")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> updateSubscription(
            @PathVariable UUID companyId,
            @Valid @RequestBody UpdateSubscriptionRequest request) {
        
        log.info("Updating company subscription: {}", companyId);
        
        UUID tenantId = SecurityContextHolder.getCurrentTenantId();
        String updatedBy = SecurityContextHolder.getCurrentUserId();
        
        companyService.updateSubscription(companyId, request, tenantId, updatedBy);
        
        return ResponseEntity.ok(ApiResponse.success(null, "Company subscription updated successfully"));
    }
}
