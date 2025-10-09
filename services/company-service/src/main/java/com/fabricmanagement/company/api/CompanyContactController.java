package com.fabricmanagement.company.api;

import com.fabricmanagement.company.application.dto.AddContactToCompanyRequest;
import com.fabricmanagement.company.application.service.CompanyContactService;
import com.fabricmanagement.shared.application.context.SecurityContext;
import com.fabricmanagement.shared.application.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Company Contact Management Controller
 * 
 * Handles company contact information.
 * Uses Spring Security's @AuthenticationPrincipal - 100% framework-native!
 * 
 * API Version: v1
 * Base Path: /{companyId}/contacts (Gateway strips /api/v1/companies prefix)
 */
@RestController
@RequestMapping("/{companyId}/contacts")
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
            @Valid @RequestBody AddContactToCompanyRequest request,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.info("Adding contact to company {}", companyId);
        
        UUID contactId = companyContactService.addContactToCompany(companyId, request, ctx.getTenantId(), ctx.getUserId());
        
        return ResponseEntity.ok(ApiResponse.success(contactId, "Contact added to company successfully"));
    }
    
    /**
     * Removes a contact from a company
     */
    @DeleteMapping("/{contactId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPANY_MANAGER')")
    public ResponseEntity<ApiResponse<Void>> removeContactFromCompany(
            @PathVariable UUID companyId,
            @PathVariable UUID contactId,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.info("Removing contact {} from company {}", contactId, companyId);
        
        companyContactService.removeContactFromCompany(companyId, contactId, ctx.getTenantId(), ctx.getUserId());
        
        return ResponseEntity.ok(ApiResponse.success(null, "Contact removed from company successfully"));
    }
    
    /**
     * Sets a contact as primary for the company
     */
    @PostMapping("/{contactId}/set-primary")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPANY_MANAGER')")
    public ResponseEntity<ApiResponse<Void>> setPrimaryContact(
            @PathVariable UUID companyId,
            @PathVariable UUID contactId,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.info("Setting contact {} as primary for company {}", contactId, companyId);
        
        companyContactService.setPrimaryContact(companyId, contactId, ctx.getTenantId(), ctx.getUserId());
        
        return ResponseEntity.ok(ApiResponse.success(null, "Primary contact set successfully"));
    }
}
