package com.fabricmanagement.user.api;

import com.fabricmanagement.shared.application.context.SecurityContext;
import com.fabricmanagement.shared.application.response.ApiResponse;
import com.fabricmanagement.shared.application.response.PagedResponse;
import com.fabricmanagement.user.api.dto.request.CreateUserRequest;
import com.fabricmanagement.user.api.dto.request.UpdateUserRequest;
import com.fabricmanagement.user.api.dto.response.UserResponse;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {
    
    private final UserService userService;
    
    @GetMapping("/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(
            @PathVariable UUID userId,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.debug("Getting user: {} for tenant: {}", userId, ctx.getTenantId());
        UserResponse user = userService.getUser(userId, ctx.getTenantId());
        return ResponseEntity.ok(ApiResponse.success(user));
    }
    
    @GetMapping("/{userId}/exists")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Boolean>> userExists(
            @PathVariable UUID userId,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.debug("Checking if user exists: {} for tenant: {}", userId, ctx.getTenantId());
        boolean exists = userService.userExists(userId, ctx.getTenantId());
        return ResponseEntity.ok(ApiResponse.success(exists));
    }
    
    @GetMapping("/company/{companyId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getUsersByCompany(
            @PathVariable UUID companyId,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.debug("Getting users for company: {} and tenant: {}", companyId, ctx.getTenantId());
        List<UserResponse> users = userService.getUsersByTenant(ctx.getTenantId());
        return ResponseEntity.ok(ApiResponse.success(users));
    }
    
    @GetMapping("/company/{companyId}/count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Integer>> getUserCountForCompany(
            @PathVariable UUID companyId,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.debug("Getting user count for company: {} and tenant: {}", companyId, ctx.getTenantId());
        int count = userService.getUserCountForTenant(ctx.getTenantId());
        return ResponseEntity.ok(ApiResponse.success(count));
    }
    
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<UUID>> createUser(
            @Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.info("Creating user: {} for tenant: {}", request.getEmail(), ctx.getTenantId());
        UUID userId = userService.createUser(request, ctx.getTenantId(), ctx.getUserId());
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(userId, "User created successfully"));
    }
    
    @PutMapping("/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> updateUser(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.info("Updating user: {} for tenant: {}", userId, ctx.getTenantId());
        userService.updateUser(userId, request, ctx.getTenantId(), ctx.getUserId());
        
        return ResponseEntity.ok(ApiResponse.success(null, "User updated successfully"));
    }
    
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @PathVariable UUID userId,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.info("Deleting user: {} for tenant: {}", userId, ctx.getTenantId());
        userService.deleteUser(userId, ctx.getTenantId(), ctx.getUserId());
        
        return ResponseEntity.ok(ApiResponse.success(null, "User deleted successfully"));
    }
    
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<UserResponse>>> listUsers(
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.debug("Listing users for tenant: {}", ctx.getTenantId());
        List<UserResponse> users = userService.listUsers(ctx.getTenantId());
        return ResponseEntity.ok(ApiResponse.success(users));
    }
    
    @GetMapping("/paged")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<PagedResponse<UserResponse>> listUsersPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.debug("Listing users for tenant: {} with pagination", ctx.getTenantId());
        
        Pageable pageable = createPageable(page, size, sort);
        PagedResponse<UserResponse> response = userService.listUsersPaginated(ctx.getTenantId(), pageable);
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<UserResponse>>> searchUsers(
            @RequestParam(required = false) String firstName,
            @RequestParam(required = false) String lastName,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.debug("Searching users for tenant: {}", ctx.getTenantId());
        List<UserResponse> users = userService.searchUsers(ctx.getTenantId(), firstName, lastName, email, status);
        return ResponseEntity.ok(ApiResponse.success(users));
    }
    
    @GetMapping("/search/paged")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<PagedResponse<UserResponse>> searchUsersPaginated(
            @RequestParam(required = false) String firstName,
            @RequestParam(required = false) String lastName,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.debug("Searching users for tenant: {}", ctx.getTenantId());
        
        Pageable pageable = createPageable(page, size, sort);
        PagedResponse<UserResponse> response = userService.searchUsersPaginated(
            ctx.getTenantId(), firstName, lastName, status, pageable
        );
        
        return ResponseEntity.ok(response);
    }
    
    private Pageable createPageable(int page, int size, String sort) {
        String[] sortParams = sort.split(",");
        String sortField = sortParams[0];
        Sort.Direction direction = sortParams.length > 1 && "desc".equalsIgnoreCase(sortParams[1]) 
                ? Sort.Direction.DESC 
                : Sort.Direction.ASC;
        
        return PageRequest.of(page, size, Sort.by(direction, sortField));
    }
}
