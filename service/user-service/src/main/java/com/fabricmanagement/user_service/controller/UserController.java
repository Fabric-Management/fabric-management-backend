package com.fabricmanagement.user_service.controller;

import com.fabricmanagement.user_service.dto.request.*;
import com.fabricmanagement.user_service.dto.response.*;
import com.fabricmanagement.user_service.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "User management operations")
public class UserController {

    private final UserService userService;

    @PostMapping
    @Operation(summary = "Create new user", description = "Creates a new user in the system")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "User created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "409", description = "Username or email already exists")
    })
    public ResponseEntity<com.fabricmanagement.user_service.dto.response.ApiResponse<UserResponse>> createUser(
            @Valid @RequestBody CreateUserRequest request,
            @RequestHeader(value = "X-User-Id", required = false) UUID createdBy) {

        UserResponse user = userService.createUser(request, createdBy);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(com.fabricmanagement.user_service.dto.response.ApiResponse.success(user));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID", description = "Retrieves user details by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User found"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<com.fabricmanagement.user_service.dto.response.ApiResponse<UserResponse>> getUserById(
            @Parameter(description = "User ID") @PathVariable UUID id) {

        UserResponse user = userService.getUserById(id);
        return ResponseEntity.ok(
                com.fabricmanagement.user_service.dto.response.ApiResponse.success(user)
        );
    }

    @GetMapping("/username/{username}")
    @Operation(summary = "Get user by username", description = "Retrieves user details by username")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User found"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<com.fabricmanagement.user_service.dto.response.ApiResponse<UserResponse>> getUserByUsername(
            @Parameter(description = "Username") @PathVariable String username) {

        UserResponse user = userService.getUserByUsername(username);
        return ResponseEntity.ok(
                com.fabricmanagement.user_service.dto.response.ApiResponse.success(user)
        );
    }

    @GetMapping("/email/{email}")
    @Operation(summary = "Get user by email", description = "Retrieves user details by email")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User found"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<com.fabricmanagement.user_service.dto.response.ApiResponse<UserResponse>> getUserByEmail(
            @Parameter(description = "Email address") @PathVariable String email) {

        UserResponse user = userService.getUserByEmail(email);
        return ResponseEntity.ok(
                com.fabricmanagement.user_service.dto.response.ApiResponse.success(user)
        );
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update user", description = "Updates user information")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "409", description = "Username or email already exists")
    })
    public ResponseEntity<com.fabricmanagement.user_service.dto.response.ApiResponse<UserResponse>> updateUser(
            @Parameter(description = "User ID") @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request) {

        UserResponse user = userService.updateUser(id, request);
        return ResponseEntity.ok(
                com.fabricmanagement.user_service.dto.response.ApiResponse.success(user)
        );
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete user", description = "Soft deletes a user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "User deleted successfully"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<Void> deleteUser(
            @Parameter(description = "User ID") @PathVariable UUID id) {

        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/search")
    @Operation(summary = "Search users", description = "Search users with filters and pagination")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Search results returned")
    })
    public ResponseEntity<com.fabricmanagement.user_service.dto.response.ApiResponse<PageResponse<UserSummaryResponse>>> searchUsers(
            @Valid @RequestBody UserSearchRequest request) {

        Page<UserSummaryResponse> users = userService.searchUsers(request);
        PageResponse<UserSummaryResponse> pageResponse = PageResponse.of(users);
        return ResponseEntity.ok(
                com.fabricmanagement.user_service.dto.response.ApiResponse.success(pageResponse)
        );
    }

    @GetMapping("/company/{companyId}")
    @Operation(summary = "Get users by company", description = "Retrieves all users belonging to a company")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Users retrieved successfully")
    })
    public ResponseEntity<com.fabricmanagement.user_service.dto.response.ApiResponse<PageResponse<UserSummaryResponse>>> getUsersByCompany(
            @Parameter(description = "Company ID") @PathVariable UUID companyId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<UserSummaryResponse> users = userService.getUsersByCompany(companyId, pageable);
        PageResponse<UserSummaryResponse> pageResponse = PageResponse.of(users);
        return ResponseEntity.ok(
                com.fabricmanagement.user_service.dto.response.ApiResponse.success(pageResponse)
        );
    }

    @GetMapping("/company/{companyId}/active")
    @Operation(summary = "Get active users by company", description = "Retrieves all active users of a company")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Active users retrieved successfully")
    })
    public ResponseEntity<com.fabricmanagement.user_service.dto.response.ApiResponse<List<UserMinimalResponse>>> getActiveUsersByCompany(
            @Parameter(description = "Company ID") @PathVariable UUID companyId) {

        List<UserMinimalResponse> users = userService.getActiveUsersByCompany(companyId);
        return ResponseEntity.ok(
                com.fabricmanagement.user_service.dto.response.ApiResponse.success(users)
        );
    }

    @GetMapping("/check/username/{username}")
    @Operation(summary = "Check username availability", description = "Checks if username is available")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Username availability checked")
    })
    public ResponseEntity<com.fabricmanagement.user_service.dto.response.ApiResponse<Boolean>> checkUsername(
            @Parameter(description = "Username to check") @PathVariable String username) {

        boolean available = userService.isUsernameAvailable(username);
        return ResponseEntity.ok(
                com.fabricmanagement.user_service.dto.response.ApiResponse.success(available)
        );
    }

    @GetMapping("/check/email/{email}")
    @Operation(summary = "Check email availability", description = "Checks if email is available")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Email availability checked")
    })
    public ResponseEntity<com.fabricmanagement.user_service.dto.response.ApiResponse<Boolean>> checkEmail(
            @Parameter(description = "Email to check") @PathVariable String email) {

        boolean available = userService.isEmailAvailable(email);
        return ResponseEntity.ok(
                com.fabricmanagement.user_service.dto.response.ApiResponse.success(available)
        );
    }

    @PostMapping("/validate")
    @Operation(summary = "Validate user", description = "Validates if user exists and can login")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Validation result returned")
    })
    public ResponseEntity<com.fabricmanagement.user_service.dto.response.ApiResponse<UserValidationResponse>> validateUser(
            @Parameter(description = "Username or email") @RequestParam String identifier) {

        UserValidationResponse validation = userService.validateUser(identifier);
        return ResponseEntity.ok(
                com.fabricmanagement.user_service.dto.response.ApiResponse.success(validation)
        );
    }
}