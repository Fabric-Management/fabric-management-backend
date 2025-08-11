package com.fabricmanagement.user_service.controller;

import com.fabricmanagement.user_service.dto.request.BulkUserOperationRequest;
import com.fabricmanagement.user_service.dto.response.*;
import com.fabricmanagement.user_service.entity.enums.Role;
import com.fabricmanagement.user_service.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Tag(name = "User Admin Operations", description = "Administrative user management operations")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {

    private final UserService userService;

    @PutMapping("/{id}/activate")
    @Operation(summary = "Activate user", description = "Activates a user account")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User activated successfully"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<com.fabricmanagement.user_service.dto.response.ApiResponse<Void>> activateUser(
            @Parameter(description = "User ID") @PathVariable UUID id) {

        userService.activateUser(id);
        return ResponseEntity.ok(
                com.fabricmanagement.user_service.dto.response.ApiResponse.success(null)
        );
    }

    @PutMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate user", description = "Deactivates a user account")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User deactivated successfully"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<com.fabricmanagement.user_service.dto.response.ApiResponse<String>> deactivateUser(
            @Parameter(description = "User ID") @PathVariable UUID id) {

        log.info("Admin deactivating user: {}", id);
        userService.deactivateUser(id);

        String message = messageService.getMessage(MessageKeys.Success.STATUS_UPDATED);
        return ResponseEntity.ok(
                com.fabricmanagement.user_service.dto.response.ApiResponse.success(null, message)
        );
    }

    @PutMapping("/{id}/suspend")
    @Operation(summary = "Suspend user", description = "Suspends a user account")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User suspended successfully"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<com.fabricmanagement.user_service.dto.response.ApiResponse<String>> suspendUser(
            @Parameter(description = "User ID") @PathVariable UUID id) {

        log.info("Admin suspending user: {}", id);
        userService.suspendUser(id);

        String message = messageService.getMessage(MessageKeys.Success.STATUS_UPDATED);
        return ResponseEntity.ok(
                com.fabricmanagement.user_service.dto.response.ApiResponse.success(null, message)
        );
    }

    @PutMapping("/{id}/unlock")
    @Operation(summary = "Unlock user", description = "Unlocks a locked user account")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User unlocked successfully"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<com.fabricmanagement.user_service.dto.response.ApiResponse<String>> unlockUser(
            @Parameter(description = "User ID") @PathVariable UUID id) {

        log.info("Admin unlocking user: {}", id);
        userService.unlockUser(id);

        String message = messageService.getMessage(MessageKeys.Success.STATUS_UPDATED);
        return ResponseEntity.ok(
                com.fabricmanagement.user_service.dto.response.ApiResponse.success(null, message)
        );
    }

    @PostMapping("/bulk")
    @Operation(summary = "Bulk user operations", description = "Performs bulk operations on multiple users")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Bulk operation completed")
    })
    public ResponseEntity<com.fabricmanagement.user_service.dto.response.ApiResponse<BulkOperationResponse>> bulkOperation(
            @Valid @RequestBody BulkUserOperationRequest request) {

        log.info("Admin performing bulk operation: {} on {} users",
                request.operation(), request.userIds().size());

        BulkOperationResponse response = userService.bulkOperation(request);

        return ResponseEntity.ok(
                com.fabricmanagement.user_service.dto.response.ApiResponse.success(response)
        );
    }

    @PutMapping("/{id}/roles/add")
    @Operation(summary = "Add roles to user", description = "Adds roles to a user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Roles added successfully"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<com.fabricmanagement.user_service.dto.response.ApiResponse<String>> addRoles(
            @Parameter(description = "User ID") @PathVariable UUID id,
            @Parameter(description = "Roles to add") @RequestBody List<Role> roles) {

        log.info("Admin adding roles {} to user: {}", roles, id);
        userService.addRoles(id, roles);

        String message = messageService.getMessage(MessageKeys.Success.ROLES_UPDATED);
        return ResponseEntity.ok(
                com.fabricmanagement.user_service.dto.response.ApiResponse.success(null, message)
        );
    }

    @PutMapping("/{id}/roles/remove")
    @Operation(summary = "Remove roles from user", description = "Removes roles from a user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Roles removed successfully"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<com.fabricmanagement.user_service.dto.response.ApiResponse<String>> removeRoles(
            @Parameter(description = "User ID") @PathVariable UUID id,
            @Parameter(description = "Roles to remove") @RequestBody List<Role> roles) {

        log.info("Admin removing roles {} from user: {}", roles, id);
        userService.removeRoles(id, roles);

        String message = messageService.getMessage(MessageKeys.Success.ROLES_UPDATED);
        return ResponseEntity.ok(
                com.fabricmanagement.user_service.dto.response.ApiResponse.success(null, message)
        );
    }

    @PutMapping("/{id}/roles")
    @Operation(summary = "Set user roles", description = "Replaces all user roles with new ones")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Roles set successfully"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<com.fabricmanagement.user_service.dto.response.ApiResponse<String>> setRoles(
            @Parameter(description = "User ID") @PathVariable UUID id,
            @Parameter(description = "New roles") @RequestBody List<Role> roles) {

        log.info("Admin setting roles {} for user: {}", roles, id);
        userService.setRoles(id, roles);

        String message = messageService.getMessage(MessageKeys.Success.ROLES_UPDATED);
        return ResponseEntity.ok(
                com.fabricmanagement.user_service.dto.response.ApiResponse.success(null, message)
        );
    }

    @GetMapping("/stats/dashboard")
    @Operation(summary = "Get dashboard statistics", description = "Retrieves user dashboard statistics")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully")
    })
    public ResponseEntity<com.fabricmanagement.user_service.dto.response.ApiResponse<UserDashboardStats>> getDashboardStats(
            @Parameter(description = "Company ID (optional)") @RequestParam(required = false) UUID companyId) {

        log.info("Admin getting dashboard stats for company: {}", companyId);
        UserDashboardStats stats = userService.getDashboardStats(companyId);

        return ResponseEntity.ok(
                com.fabricmanagement.user_service.dto.response.ApiResponse.success(stats)
        );
    }

    @GetMapping("/stats/company/{companyId}")
    @Operation(summary = "Get company statistics", description = "Retrieves company-specific user statistics")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully")
    })
    public ResponseEntity<com.fabricmanagement.user_service.dto.response.ApiResponse<CompanyUserStats>> getCompanyStats(
            @Parameter(description = "Company ID") @PathVariable UUID companyId) {

        log.info("Admin getting stats for company: {}", companyId);
        CompanyUserStats stats = userService.getCompanyStats(companyId);

        return ResponseEntity.ok(
                com.fabricmanagement.user_service.dto.response.ApiResponse.success(stats)
        );
    }

    @GetMapping("/stats/login")
    @Operation(summary = "Get login statistics", description = "Retrieves login activity statistics")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully")
    })
    public ResponseEntity<com.fabricmanagement.user_service.dto.response.ApiResponse<LoginActivityStats>> getLoginStats(
            @Parameter(description = "Company ID (optional)") @RequestParam(required = false) UUID companyId) {

        log.info("Admin getting login stats for company: {}", companyId);
        LoginActivityStats stats = userService.getLoginStats(companyId);

        return ResponseEntity.ok(
                com.fabricmanagement.user_service.dto.response.ApiResponse.success(stats)
        );
    }
}