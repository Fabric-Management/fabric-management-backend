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
}
