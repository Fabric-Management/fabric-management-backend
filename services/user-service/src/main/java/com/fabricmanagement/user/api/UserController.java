package com.fabricmanagement.user.api;

import com.fabricmanagement.shared.application.annotation.CurrentSecurityContext;
import com.fabricmanagement.shared.application.context.SecurityContext;
import com.fabricmanagement.shared.application.response.ApiResponse;
import com.fabricmanagement.shared.application.response.PagedResponse;
import com.fabricmanagement.user.api.dto.CreateUserRequest;
import com.fabricmanagement.user.api.dto.UpdateUserRequest;
import com.fabricmanagement.user.api.dto.UserResponse;
import com.fabricmanagement.user.application.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
 * Benefits of @CurrentSecurityContext:
 * - No repetitive SecurityContextHolder.getCurrentTenantId() calls
 * - Cleaner and more testable code
 * - Single source of truth for security context
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
    public ResponseEntity<ApiResponse<UserResponse>> getUser(
            @PathVariable UUID userId,
            @CurrentSecurityContext SecurityContext ctx) {
        
        log.debug("Getting user: {} for tenant: {}", userId, ctx.getTenantId());
        
        UserResponse user = userService.getUser(userId, ctx.getTenantId());
        return ResponseEntity.ok(ApiResponse.success(user));
    }
    
    /**
     * Checks if a user exists
     * Required by Company Service
     */
    @GetMapping("/{userId}/exists")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Boolean>> userExists(
            @PathVariable UUID userId,
            @CurrentSecurityContext SecurityContext ctx) {
        
        log.debug("Checking if user exists: {} for tenant: {}", userId, ctx.getTenantId());
        
        boolean exists = userService.userExists(userId, ctx.getTenantId());
        return ResponseEntity.ok(ApiResponse.success(exists));
    }
    
    /**
     * Gets users by company ID
     * Required by Company Service
     */
    @GetMapping("/company/{companyId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getUsersByCompany(
            @PathVariable UUID companyId,
            @CurrentSecurityContext SecurityContext ctx) {
        
        log.debug("Getting users for company: {} and tenant: {}", companyId, ctx.getTenantId());
        
        List<UserResponse> users = userService.getUsersByCompany(companyId, ctx.getTenantId());
        return ResponseEntity.ok(ApiResponse.success(users));
    }
    
    /**
     * Gets user count for a company
     * Required by Company Service
     */
    @GetMapping("/company/{companyId}/count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Integer>> getUserCountForCompany(
            @PathVariable UUID companyId,
            @CurrentSecurityContext SecurityContext ctx) {
        
        log.debug("Getting user count for company: {} and tenant: {}", companyId, ctx.getTenantId());
        
        int count = userService.getUserCountForCompany(companyId, ctx.getTenantId());
        return ResponseEntity.ok(ApiResponse.success(count));
    }
    
    /**
     * Creates a new user
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UUID>> createUser(
            @Valid @RequestBody CreateUserRequest request,
            @CurrentSecurityContext SecurityContext ctx) {
        
        log.info("Creating user: {} for tenant: {}", request.getEmail(), ctx.getTenantId());
        
        UUID userId = userService.createUser(request, ctx.getTenantId(), ctx.getUserId());
        
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
            @Valid @RequestBody UpdateUserRequest request,
            @CurrentSecurityContext SecurityContext ctx) {
        
        log.info("Updating user: {} for tenant: {}", userId, ctx.getTenantId());
        
        userService.updateUser(userId, request, ctx.getTenantId(), ctx.getUserId());
        
        return ResponseEntity.ok(ApiResponse.success(null, "User updated successfully"));
    }
    
    /**
     * Deletes a user (soft delete)
     */
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @PathVariable UUID userId,
            @CurrentSecurityContext SecurityContext ctx) {
        
        log.info("Deleting user: {} for tenant: {}", userId, ctx.getTenantId());
        
        userService.deleteUser(userId, ctx.getTenantId(), ctx.getUserId());
        
        return ResponseEntity.ok(ApiResponse.success(null, "User deleted successfully"));
    }
    
    /**
     * Lists all users for the current tenant (non-paginated)
     * 
     * For large datasets, use the paginated version with ?page=0&size=20
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<UserResponse>>> listUsers(
            @CurrentSecurityContext SecurityContext ctx) {
        
        log.debug("Listing users for tenant: {}", ctx.getTenantId());
        
        List<UserResponse> users = userService.listUsers(ctx.getTenantId());
        return ResponseEntity.ok(ApiResponse.success(users));
    }
    
    /**
     * Lists users for the current tenant with pagination
     * 
     * Query params:
     * - page: Page number (0-indexed, default: 0)
     * - size: Items per page (default: 20)
     * - sort: Sort field and direction (e.g., firstName,asc or createdAt,desc)
     * 
     * Example: GET /api/v1/users/paged?page=0&size=10&sort=firstName,asc
     */
    @GetMapping("/paged")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PagedResponse<UserResponse>> listUsersPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @CurrentSecurityContext SecurityContext ctx) {
        
        log.debug("Listing users for tenant: {} with pagination (page: {}, size: {})", 
                  ctx.getTenantId(), page, size);
        
        // Parse sort parameter
        String[] sortParams = sort.split(",");
        String sortField = sortParams[0];
        Sort.Direction direction = sortParams.length > 1 && "desc".equalsIgnoreCase(sortParams[1]) 
                ? Sort.Direction.DESC 
                : Sort.Direction.ASC;
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));
        PagedResponse<UserResponse> response = userService.listUsersPaginated(ctx.getTenantId(), pageable);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Searches users by criteria (non-paginated)
     * 
     * Note: For large datasets, use /search/paged endpoint
     */
    @GetMapping("/search")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<UserResponse>>> searchUsers(
            @RequestParam(required = false) String firstName,
            @RequestParam(required = false) String lastName,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String status,
            @CurrentSecurityContext SecurityContext ctx) {
        
        log.debug("Searching users for tenant {} with criteria: firstName={}, lastName={}, email={}, status={}",
                ctx.getTenantId(), firstName, lastName, email, status);
        
        List<UserResponse> users = userService.searchUsers(ctx.getTenantId(), firstName, lastName, email, status);
        return ResponseEntity.ok(ApiResponse.success(users));
    }
    
    /**
     * Searches users by criteria with pagination
     * 
     * Query params:
     * - firstName: Filter by first name (optional)
     * - lastName: Filter by last name (optional)
     * - status: Filter by status (optional)
     * - page: Page number (0-indexed, default: 0)
     * - size: Items per page (default: 20)
     * - sort: Sort field and direction (default: createdAt,desc)
     * 
     * Example: GET /api/v1/users/search/paged?firstName=John&page=0&size=10&sort=lastName,asc
     * 
     * Note: Email search not supported (requires batch contact API - see IMPROVEMENTS.md)
     */
    @GetMapping("/search/paged")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PagedResponse<UserResponse>> searchUsersPaginated(
            @RequestParam(required = false) String firstName,
            @RequestParam(required = false) String lastName,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @CurrentSecurityContext SecurityContext ctx) {
        
        log.debug("Searching users for tenant {} with pagination - criteria: firstName={}, lastName={}, status={}, page={}, size={}",
                ctx.getTenantId(), firstName, lastName, status, page, size);
        
        // Parse sort parameter
        String[] sortParams = sort.split(",");
        String sortField = sortParams[0];
        Sort.Direction direction = sortParams.length > 1 && "desc".equalsIgnoreCase(sortParams[1]) 
                ? Sort.Direction.DESC 
                : Sort.Direction.ASC;
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));
        PagedResponse<UserResponse> response = userService.searchUsersPaginated(
            ctx.getTenantId(), firstName, lastName, status, pageable
        );
        
        return ResponseEntity.ok(response);
    }
}