package com.fabricmanagement.company.api;

import com.fabricmanagement.company.application.dto.AddUserToCompanyRequest;
import com.fabricmanagement.company.application.service.CompanyUserService;
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
 * Company User Management Controller
 * 
 * Handles user-company relationships.
 * Follows Clean Architecture principles.
 * 
 * API Version: v1
 * Base Path: /{companyId}/users (Gateway strips /api/v1/companies prefix)
 */
@RestController
@RequestMapping("/{companyId}/users")
@RequiredArgsConstructor
@Slf4j
public class CompanyUserController {
    
    private final CompanyUserService companyUserService;
    
    /**
     * Adds a user to a company
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPANY_MANAGER')")
    public ResponseEntity<ApiResponse<Void>> addUserToCompany(
            @PathVariable UUID companyId,
            @Valid @RequestBody AddUserToCompanyRequest request) {
        
        log.info("Adding user {} to company {}", request.getUserId(), companyId);
        
        UUID tenantId = SecurityContextHolder.getCurrentTenantId();
        String addedBy = SecurityContextHolder.getCurrentUserId();
        
        companyUserService.addUserToCompany(companyId, request, tenantId, addedBy);
        
        return ResponseEntity.ok(ApiResponse.success(null, "User added to company successfully"));
    }
    
    /**
     * Removes a user from a company
     */
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPANY_MANAGER')")
    public ResponseEntity<ApiResponse<Void>> removeUserFromCompany(
            @PathVariable UUID companyId,
            @PathVariable UUID userId) {
        
        log.info("Removing user {} from company {}", userId, companyId);
        
        UUID tenantId = SecurityContextHolder.getCurrentTenantId();
        String removedBy = SecurityContextHolder.getCurrentUserId();
        
        companyUserService.removeUserFromCompany(companyId, userId, tenantId, removedBy);
        
        return ResponseEntity.ok(ApiResponse.success(null, "User removed from company successfully"));
    }
    
    /**
     * Updates a user's role in a company
     */
    @PutMapping("/{userId}/role")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPANY_MANAGER')")
    public ResponseEntity<ApiResponse<Void>> updateUserRole(
            @PathVariable UUID companyId,
            @PathVariable UUID userId,
            @RequestParam String role) {
        
        log.info("Updating role for user {} in company {} to {}", userId, companyId, role);
        
        UUID tenantId = SecurityContextHolder.getCurrentTenantId();
        String updatedBy = SecurityContextHolder.getCurrentUserId();
        
        companyUserService.updateUserRole(companyId, userId, role, tenantId, updatedBy);
        
        return ResponseEntity.ok(ApiResponse.success(null, "User role updated successfully"));
    }
}
