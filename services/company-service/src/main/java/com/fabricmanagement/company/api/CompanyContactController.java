package com.fabricmanagement.company.api;

import com.fabricmanagement.company.application.dto.AddContactToCompanyRequest;
import com.fabricmanagement.company.application.service.CompanyContactService;
import com.fabricmanagement.shared.application.response.ApiResponse;
import com.fabricmanagement.shared.infrastructure.security.SecurityContextHolder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Company Contact Management Controller
 * 
 * Handles company contact information.
 * Follows Clean Architecture principles.
 * 
 * API Version: v1
 * Base Path: /api/v1/companies/{companyId}/contacts
 */
@RestController
@RequestMapping("/api/v1/companies/{companyId}/contacts")
@RequiredArgsConstructor
@Slf4j
public class CompanyContactController {
    
    private final CompanyContactService companyContactService;
    
    /**
     * Adds a contact to a company
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPANY_MANAGER')")
    public ResponseEntity<ApiResponse<UUID>> addContactToCompany(
            @PathVariable UUID companyId,
            @Valid @RequestBody AddContactToCompanyRequest request) {
        
        log.info("Adding contact to company {}", companyId);
        
        UUID tenantId = SecurityContextHolder.getCurrentTenantId();
        String addedBy = SecurityContextHolder.getCurrentUserId();
        
        UUID contactId = companyContactService.addContactToCompany(companyId, request, tenantId, addedBy);
        
        return ResponseEntity.ok(ApiResponse.success(contactId, "Contact added to company successfully"));
    }
    
    /**
     * Removes a contact from a company
     */
    @DeleteMapping("/{contactId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPANY_MANAGER')")
    public ResponseEntity<ApiResponse<Void>> removeContactFromCompany(
            @PathVariable UUID companyId,
            @PathVariable UUID contactId) {
        
        log.info("Removing contact {} from company {}", contactId, companyId);
        
        UUID tenantId = SecurityContextHolder.getCurrentTenantId();
        String removedBy = SecurityContextHolder.getCurrentUserId();
        
        companyContactService.removeContactFromCompany(companyId, contactId, tenantId, removedBy);
        
        return ResponseEntity.ok(ApiResponse.success(null, "Contact removed from company successfully"));
    }
    
    /**
     * Sets a contact as primary for the company
     */
    @PostMapping("/{contactId}/set-primary")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPANY_MANAGER')")
    public ResponseEntity<ApiResponse<Void>> setPrimaryContact(
            @PathVariable UUID companyId,
            @PathVariable UUID contactId) {
        
        log.info("Setting contact {} as primary for company {}", contactId, companyId);
        
        UUID tenantId = SecurityContextHolder.getCurrentTenantId();
        String updatedBy = SecurityContextHolder.getCurrentUserId();
        
        companyContactService.setPrimaryContact(companyId, contactId, tenantId, updatedBy);
        
        return ResponseEntity.ok(ApiResponse.success(null, "Primary contact set successfully"));
    }
}
