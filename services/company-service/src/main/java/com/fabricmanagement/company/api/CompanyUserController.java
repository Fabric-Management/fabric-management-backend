package com.fabricmanagement.company.api;

import com.fabricmanagement.company.application.service.CompanyUserService;
import com.fabricmanagement.company.infrastructure.client.dto.UserDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Company User REST Controller
 * 
 * Provides API endpoints for managing company-user relationships
 */
@RestController
@RequestMapping("/api/companies/{companyId}/users")
@RequiredArgsConstructor
@Slf4j
public class CompanyUserController {
    
    private final CompanyUserService companyUserService;
    
    /**
     * Gets the current tenant ID from security context
     */
    private UUID getCurrentTenantId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof String) {
                try {
                    return UUID.fromString((String) principal);
                } catch (Exception e) {
                    log.warn("Could not parse tenant ID from principal, using default");
                }
            }
        }
        return UUID.randomUUID();
    }
    
    /**
     * Gets all users for a company
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<UserDto>> getCompanyUsers(@PathVariable UUID companyId) {
        log.debug("Getting users for company: {}", companyId);
        
        UUID tenantId = getCurrentTenantId();
        List<UserDto> users = companyUserService.getCompanyUsers(companyId, tenantId);
        
        return ResponseEntity.ok(users);
    }
    
    /**
     * Gets user count for a company
     */
    @GetMapping("/count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Integer> getCompanyUserCount(@PathVariable UUID companyId) {
        log.debug("Getting user count for company: {}", companyId);
        
        UUID tenantId = getCurrentTenantId();
        int count = companyUserService.getCompanyUserCount(companyId, tenantId);
        
        return ResponseEntity.ok(count);
    }
    
    /**
     * Adds a user to a company
     */
    @PostMapping("/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPANY_MANAGER')")
    public ResponseEntity<Void> addUserToCompany(
            @PathVariable UUID companyId,
            @PathVariable UUID userId) {
        
        log.info("Adding user {} to company {}", userId, companyId);
        
        UUID tenantId = getCurrentTenantId();
        companyUserService.addUserToCompany(companyId, userId, tenantId);
        
        return ResponseEntity.ok().build();
    }
    
    /**
     * Removes a user from a company
     */
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPANY_MANAGER')")
    public ResponseEntity<Void> removeUserFromCompany(
            @PathVariable UUID companyId,
            @PathVariable UUID userId) {
        
        log.info("Removing user {} from company {}", userId, companyId);
        
        UUID tenantId = getCurrentTenantId();
        companyUserService.removeUserFromCompany(companyId, userId, tenantId);
        
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Syncs user count from User Service
     */
    @PostMapping("/sync")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> syncUserCount(@PathVariable UUID companyId) {
        log.info("Syncing user count for company: {}", companyId);
        
        UUID tenantId = getCurrentTenantId();
        companyUserService.syncUserCount(companyId, tenantId);
        
        return ResponseEntity.ok().build();
    }
}

