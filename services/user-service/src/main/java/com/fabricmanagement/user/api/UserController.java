package com.fabricmanagement.user.api;

import com.fabricmanagement.user.api.dto.CreateUserRequest;
import com.fabricmanagement.user.api.dto.UpdateUserRequest;
import com.fabricmanagement.user.api.dto.UserResponse;
import com.fabricmanagement.user.application.service.UserService;
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
 * User REST Controller
 * 
 * Provides API endpoints for user management
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {
    
    private final UserService userService;
    
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
     * Gets a user by ID
     * Required by Company Service
     */
    @GetMapping("/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserResponse> getUser(@PathVariable UUID userId) {
        log.debug("Getting user: {}", userId);
        
        UUID tenantId = getCurrentTenantId();
        UserResponse user = userService.getUser(userId, tenantId);
        
        return ResponseEntity.ok(user);
    }
    
    /**
     * Checks if a user exists
     * Required by Company Service
     */
    @GetMapping("/{userId}/exists")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Boolean> userExists(@PathVariable UUID userId) {
        log.debug("Checking if user exists: {}", userId);
        
        UUID tenantId = getCurrentTenantId();
        boolean exists = userService.userExists(userId, tenantId);
        
        return ResponseEntity.ok(exists);
    }
    
    /**
     * Gets users by company ID
     * Required by Company Service
     */
    @GetMapping("/company/{companyId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<UserResponse>> getUsersByCompany(@PathVariable UUID companyId) {
        log.debug("Getting users for company: {}", companyId);
        
        UUID tenantId = getCurrentTenantId();
        List<UserResponse> users = userService.getUsersByCompany(companyId, tenantId);
        
        return ResponseEntity.ok(users);
    }
    
    /**
     * Gets user count for a company
     * Required by Company Service
     */
    @GetMapping("/company/{companyId}/count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Integer> getUserCountForCompany(@PathVariable UUID companyId) {
        log.debug("Getting user count for company: {}", companyId);
        
        UUID tenantId = getCurrentTenantId();
        int count = userService.getUserCountForCompany(companyId, tenantId);
        
        return ResponseEntity.ok(count);
    }
    
    /**
     * Creates a new user
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UUID> createUser(@Valid @RequestBody CreateUserRequest request) {
        log.info("Creating user: {}", request.getEmail());
        
        UUID tenantId = getCurrentTenantId();
        String createdBy = getCurrentUserId();
        
        UUID userId = userService.createUser(request, tenantId, createdBy);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(userId);
    }
    
    /**
     * Updates a user
     */
    @PutMapping("/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> updateUser(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserRequest request) {
        
        log.info("Updating user: {}", userId);
        
        UUID tenantId = getCurrentTenantId();
        String updatedBy = getCurrentUserId();
        
        userService.updateUser(userId, request, tenantId, updatedBy);
        
        return ResponseEntity.ok().build();
    }
    
    /**
     * Deletes a user (soft delete)
     */
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID userId) {
        log.info("Deleting user: {}", userId);
        
        UUID tenantId = getCurrentTenantId();
        String deletedBy = getCurrentUserId();
        
        userService.deleteUser(userId, tenantId, deletedBy);
        
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Lists all users for the current tenant
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponse>> listUsers() {
        log.debug("Listing users");
        
        UUID tenantId = getCurrentTenantId();
        List<UserResponse> users = userService.listUsers(tenantId);
        
        return ResponseEntity.ok(users);
    }
    
    /**
     * Searches users by criteria
     */
    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<UserResponse>> searchUsers(@RequestParam String q) {
        log.debug("Searching users with term: {}", q);
        
        UUID tenantId = getCurrentTenantId();
        List<UserResponse> users = userService.searchUsers(q, tenantId);
        
        return ResponseEntity.ok(users);
    }
}

