package com.fabricmanagement.user.infrastructure.web.controller;

import com.fabricmanagement.common.core.application.dto.ApiResponse;
import com.fabricmanagement.common.core.application.dto.PageResponse;
import com.fabricmanagement.user.application.dto.user.request.CreateUserRequest;
import com.fabricmanagement.user.application.dto.user.request.UpdateUserRequest;
import com.fabricmanagement.user.application.dto.user.response.UserDetailResponse;
import com.fabricmanagement.user.application.dto.user.response.UserResponse;
import com.fabricmanagement.user.application.port.in.command.*;
import com.fabricmanagement.user.application.port.in.query.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for user profile management operations.
 * Single responsibility: Handle HTTP requests for user profile operations.
 * Delegates business logic to use case services.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User Profiles", description = "User profile management endpoints")
@Slf4j
@Validated
public class UserController {

    // Command use cases
    private final CreateUserUseCase createUserUseCase;
    private final UpdateUserUseCase updateUserUseCase;
    private final DeleteUserUseCase deleteUserUseCase;
    private final ActivateUserUseCase activateUserUseCase;
    private final DeactivateUserUseCase deactivateUserUseCase;
    
    // Query use cases
    private final GetUserUseCase getUserUseCase;
    private final GetAllUsersUseCase getAllUsersUseCase;
    private final SearchUsersUseCase searchUsersUseCase;

    /**
     * Creates a new user profile.
     */
    @PostMapping
    @Operation(summary = "Create user profile", description = "Creates a new user profile")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER_ADMIN')")
    public ResponseEntity<ApiResponse<UserDetailResponse>> createUser(
        @Valid @RequestBody CreateUserRequest request
    ) {
        log.info("Creating user profile: {} {}", request.firstName(), request.lastName());
        UserDetailResponse response = createUserUseCase.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(response, "User profile created successfully"));
    }

    /**
     * Gets a user profile by ID.
     */
    @GetMapping("/{userId}")
    @Operation(summary = "Get user profile", description = "Gets user profile by ID")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER_ADMIN') or hasRole('USER')")
    public ResponseEntity<ApiResponse<UserDetailResponse>> getUserById(
        @PathVariable @Parameter(description = "User ID") UUID userId
    ) {
        log.info("Fetching user profile: {}", userId);
        UserDetailResponse response = getUserUseCase.getUserById(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Updates a user profile.
     */
    @PutMapping("/{userId}")
    @Operation(summary = "Update user profile", description = "Updates user profile information")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER_ADMIN') or @userService.isOwner(#userId)")
    public ResponseEntity<ApiResponse<UserDetailResponse>> updateUser(
        @PathVariable @Parameter(description = "User ID") UUID userId,
        @Valid @RequestBody UpdateUserRequest request
    ) {
        log.info("Updating user profile: {}", userId);
        UserDetailResponse response = updateUserUseCase.updateUser(userId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "User profile updated successfully"));
    }

    /**
     * Deactivates a user profile.
     */
    @PutMapping("/{userId}/deactivate")
    @Operation(summary = "Deactivate user", description = "Deactivates a user profile")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivateUser(
        @PathVariable @Parameter(description = "User ID") UUID userId
    ) {
        log.info("Deactivating user profile: {}", userId);
        deactivateUserUseCase.deactivateUser(userId);
        return ResponseEntity.ok(ApiResponse.success(null, "User profile deactivated successfully"));
    }

    /**
     * Activates a user profile.
     */
    @PutMapping("/{userId}/activate")
    @Operation(summary = "Activate user", description = "Activates a user profile")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER_ADMIN')")
    public ResponseEntity<ApiResponse<UserDetailResponse>> activateUser(
        @PathVariable @Parameter(description = "User ID") UUID userId
    ) {
        log.info("Activating user profile: {}", userId);
        UserDetailResponse response = activateUserUseCase.activateUser(userId);
        return ResponseEntity.ok(ApiResponse.success(response, "User profile activated successfully"));
    }

    /**
     * Permanently deletes a user profile.
     */
    @DeleteMapping("/{userId}")
    @Operation(summary = "Delete user profile", description = "Permanently deletes a user profile")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
        @PathVariable @Parameter(description = "User ID") UUID userId
    ) {
        log.info("Deleting user profile: {}", userId);
        deleteUserUseCase.deleteUser(userId);
        return ResponseEntity.ok(ApiResponse.success(null, "User profile deleted successfully"));
    }

    /**
     * Gets all user profiles with pagination.
     */
    @GetMapping
    @Operation(summary = "List user profiles", description = "Gets all user profiles with pagination")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER_ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> getUsers(
        @Parameter(description = "Page number") @RequestParam(defaultValue = "0") @Min(0) int page,
        @Parameter(description = "Page size") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
        @Parameter(description = "Sort by") @RequestParam(defaultValue = "firstName") String sortBy,
        @Parameter(description = "Sort direction") @RequestParam(defaultValue = "ASC") String sortDirection
    ) {
        log.info("Fetching user profiles - page: {}, size: {}", page, size);
        PageResponse<UserResponse> response = getAllUsersUseCase.getAllUsers(page, size, sortBy, sortDirection);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Searches user profiles by name or job title.
     */
    @GetMapping("/search")
    @Operation(summary = "Search user profiles", description = "Searches user profiles by name or job title")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER_ADMIN') or hasRole('USER')")
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> searchUsers(
        @Parameter(description = "Search query") @RequestParam String query,
        @Parameter(description = "Page number") @RequestParam(defaultValue = "0") @Min(0) int page,
        @Parameter(description = "Page size") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        log.info("Searching user profiles with query: {}", query);
        PageResponse<UserResponse> response = searchUsersUseCase.searchUsers(query, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}

