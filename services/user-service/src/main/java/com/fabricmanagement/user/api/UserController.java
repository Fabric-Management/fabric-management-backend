package com.fabricmanagement.user.api;

import com.fabricmanagement.shared.application.response.ApiResponse;
import com.fabricmanagement.shared.infrastructure.security.SecurityContextHolder;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * User REST Controller
 * 
 * Provides API endpoints for user management.
 * Follows Clean Architecture principles - only handles HTTP concerns.
 * 
 * API Version: v1
 * Base Path: /api/v1/users
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {
    
    private final UserService userService;
    
    /**
     * Gets a user by ID
     * Required by Company Service
     */
    @GetMapping("/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable UUID userId) {
        log.debug("Getting user: {}", userId);
        
        UUID tenantId = SecurityContextHolder.getCurrentTenantId();
        UserResponse user = userService.getUser(userId, tenantId);
        
        return ResponseEntity.ok(ApiResponse.success(user));
    }
    
    /**
     * Checks if a user exists
     * Required by Company Service
     */
    @GetMapping("/{userId}/exists")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Boolean>> userExists(@PathVariable UUID userId) {
        log.debug("Checking if user exists: {}", userId);
        
        UUID tenantId = SecurityContextHolder.getCurrentTenantId();
        boolean exists = userService.userExists(userId, tenantId);
        
        return ResponseEntity.ok(ApiResponse.success(exists));
    }
    
    /**
     * Gets users by company ID
     * Required by Company Service
     */
    @GetMapping("/company/{companyId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getUsersByCompany(@PathVariable UUID companyId) {
        log.debug("Getting users for company: {}", companyId);
        
        UUID tenantId = SecurityContextHolder.getCurrentTenantId();
        List<UserResponse> users = userService.getUsersByCompany(companyId, tenantId);
        
        return ResponseEntity.ok(ApiResponse.success(users));
    }
    
    /**
     * Gets user count for a company
     * Required by Company Service
     */
    @GetMapping("/company/{companyId}/count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Integer>> getUserCountForCompany(@PathVariable UUID companyId) {
        log.debug("Getting user count for company: {}", companyId);
        
        UUID tenantId = SecurityContextHolder.getCurrentTenantId();
        int count = userService.getUserCountForCompany(companyId, tenantId);
        
        return ResponseEntity.ok(ApiResponse.success(count));
    }
    
    /**
     * Creates a new user
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UUID>> createUser(@Valid @RequestBody CreateUserRequest request) {
        log.info("Creating user: {}", request.getEmail());
        
        UUID tenantId = SecurityContextHolder.getCurrentTenantId();
        String createdBy = SecurityContextHolder.getCurrentUserId();
        
        UUID userId = userService.createUser(request, tenantId, createdBy);
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(userId, "User created successfully"));
    }
    
    /**
     * Updates a user
     */
    @PutMapping("/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> updateUser(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserRequest request) {
        
        log.info("Updating user: {}", userId);
        
        UUID tenantId = SecurityContextHolder.getCurrentTenantId();
        String updatedBy = SecurityContextHolder.getCurrentUserId();
        
        userService.updateUser(userId, request, tenantId, updatedBy);
        
        return ResponseEntity.ok(ApiResponse.success(null, "User updated successfully"));
    }
    
    /**
     * Deletes a user (soft delete)
     */
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable UUID userId) {
        log.info("Deleting user: {}", userId);
        
        UUID tenantId = SecurityContextHolder.getCurrentTenantId();
        String deletedBy = SecurityContextHolder.getCurrentUserId();
        
        userService.deleteUser(userId, tenantId, deletedBy);
        
        return ResponseEntity.ok(ApiResponse.success(null, "User deleted successfully"));
    }
    
    /**
     * Lists all users for the current tenant
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<UserResponse>>> listUsers() {
        log.debug("Listing users");
        
        UUID tenantId = SecurityContextHolder.getCurrentTenantId();
        List<UserResponse> users = userService.listUsers(tenantId);
        
        return ResponseEntity.ok(ApiResponse.success(users));
    }
    
    /**
     * Searches users by criteria
     */
    @GetMapping("/search")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<UserResponse>>> searchUsers(
            @RequestParam(required = false) String firstName,
            @RequestParam(required = false) String lastName,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String status) {
        
        log.debug("Searching users with criteria: firstName={}, lastName={}, email={}, status={}",
                firstName, lastName, email, status);
        
        UUID tenantId = SecurityContextHolder.getCurrentTenantId();
        List<UserResponse> users = userService.searchUsers(tenantId, firstName, lastName, email, status);
        
        return ResponseEntity.ok(ApiResponse.success(users));
    }
}