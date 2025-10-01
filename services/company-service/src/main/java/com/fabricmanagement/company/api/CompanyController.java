package com.fabricmanagement.company.api;

import com.fabricmanagement.company.application.dto.*;
import com.fabricmanagement.company.application.service.CompanyService;
import com.fabricmanagement.company.domain.valueobject.CompanyStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Company REST Controller
 * 
 * Provides API endpoints for company management
 */
@RestController
@RequestMapping("/companies")
@RequiredArgsConstructor
@Slf4j
public class CompanyController {
    
    private final CompanyService companyService;
    
    /**
     * Gets the current tenant ID from security context
     */
    private UUID getCurrentTenantId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            // In a real implementation, this would extract tenant ID from JWT or session
            // For now, we'll use a placeholder
            Object principal = authentication.getPrincipal();
            if (principal instanceof String) {
                // Try to parse as UUID, if fails return a default tenant ID
                try {
                    return UUID.fromString((String) principal);
                } catch (Exception e) {
                    log.warn("Could not parse tenant ID from principal, using default");
                }
            }
        }
        // Return a default tenant ID for development
        return UUID.randomUUID();
    }
    
    /**
     * Gets the current user ID from security context
     */
    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        return "system";
    }
    
    /**
     * Creates a new company
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPANY_MANAGER')")
    public ResponseEntity<UUID> createCompany(@Valid @RequestBody CreateCompanyRequest request) {
        log.info("Creating company: {}", request.getName());
        
        UUID tenantId = getCurrentTenantId();
        String createdBy = getCurrentUserId();
        
        UUID companyId = companyService.createCompany(request, tenantId, createdBy);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(companyId);
    }
    
    /**
     * Gets a company by ID
     */
    @GetMapping("/{companyId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CompanyResponse> getCompany(@PathVariable UUID companyId) {
        log.debug("Getting company: {}", companyId);
        
        UUID tenantId = getCurrentTenantId();
        CompanyResponse company = companyService.getCompany(companyId, tenantId);
        
        return ResponseEntity.ok(company);
    }
    
    /**
     * Lists all companies for the current tenant
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CompanyResponse>> listCompanies() {
        log.debug("Listing companies");
        
        UUID tenantId = getCurrentTenantId();
        List<CompanyResponse> companies = companyService.listCompanies(tenantId);
        
        return ResponseEntity.ok(companies);
    }
    
    /**
     * Searches companies by name
     */
    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CompanyResponse>> searchCompanies(@RequestParam String q) {
        log.debug("Searching companies with term: {}", q);
        
        UUID tenantId = getCurrentTenantId();
        List<CompanyResponse> companies = companyService.searchCompanies(q, tenantId);
        
        return ResponseEntity.ok(companies);
    }
    
    /**
     * Gets companies by status
     */
    @GetMapping("/by-status/{status}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CompanyResponse>> getCompaniesByStatus(@PathVariable CompanyStatus status) {
        log.debug("Getting companies by status: {}", status);
        
        UUID tenantId = getCurrentTenantId();
        List<CompanyResponse> companies = companyService.getCompaniesByStatus(status, tenantId);
        
        return ResponseEntity.ok(companies);
    }
    
    /**
     * Updates company information
     */
    @PutMapping("/{companyId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPANY_MANAGER')")
    public ResponseEntity<Void> updateCompany(
            @PathVariable UUID companyId,
            @Valid @RequestBody UpdateCompanyRequest request) {
        
        log.info("Updating company: {}", companyId);
        
        UUID tenantId = getCurrentTenantId();
        String updatedBy = getCurrentUserId();
        
        companyService.updateCompany(companyId, request, tenantId, updatedBy);
        
        return ResponseEntity.ok().build();
    }
    
    /**
     * Deletes a company (soft delete)
     */
    @DeleteMapping("/{companyId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteCompany(@PathVariable UUID companyId) {
        log.info("Deleting company: {}", companyId);
        
        UUID tenantId = getCurrentTenantId();
        String deletedBy = getCurrentUserId();
        
        companyService.deleteCompany(companyId, tenantId, deletedBy);
        
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Updates company settings
     */
    @PutMapping("/{companyId}/settings")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPANY_MANAGER')")
    public ResponseEntity<Void> updateSettings(
            @PathVariable UUID companyId,
            @Valid @RequestBody UpdateSettingsRequest request) {
        
        log.info("Updating settings for company: {}", companyId);
        
        UUID tenantId = getCurrentTenantId();
        String updatedBy = getCurrentUserId();
        
        companyService.updateSettings(companyId, request, tenantId, updatedBy);
        
        return ResponseEntity.ok().build();
    }
    
    /**
     * Updates company subscription
     */
    @PutMapping("/{companyId}/subscription")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> updateSubscription(
            @PathVariable UUID companyId,
            @Valid @RequestBody UpdateSubscriptionRequest request) {
        
        log.info("Updating subscription for company: {}", companyId);
        
        UUID tenantId = getCurrentTenantId();
        String updatedBy = getCurrentUserId();
        
        companyService.updateSubscription(companyId, request, tenantId, updatedBy);
        
        return ResponseEntity.ok().build();
    }
    
    /**
     * Activates a company
     */
    @PutMapping("/{companyId}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> activateCompany(@PathVariable UUID companyId) {
        log.info("Activating company: {}", companyId);
        
        UUID tenantId = getCurrentTenantId();
        String updatedBy = getCurrentUserId();
        
        companyService.activateCompany(companyId, tenantId, updatedBy);
        
        return ResponseEntity.ok().build();
    }
    
    /**
     * Deactivates a company
     */
    @PutMapping("/{companyId}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivateCompany(@PathVariable UUID companyId) {
        log.info("Deactivating company: {}", companyId);
        
        UUID tenantId = getCurrentTenantId();
        String updatedBy = getCurrentUserId();
        
        companyService.deactivateCompany(companyId, tenantId, updatedBy);
        
        return ResponseEntity.ok().build();
    }
}

