package com.fabricmanagement.common.platform.user.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.platform.user.app.UserService;
import com.fabricmanagement.common.platform.user.dto.CreateUserRequest;
import com.fabricmanagement.common.platform.user.dto.UpdateUserRequest;
import com.fabricmanagement.common.platform.user.dto.UserDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/common/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<ApiResponse<UserDto>> createUser(@Valid @RequestBody CreateUserRequest request) {
        log.info("Creating user: contactValue={}", request.getContactValue());

        UserDto created = userService.createUser(request);

        return ResponseEntity.ok(ApiResponse.success(created, "User created successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserDto>> getUser(@PathVariable UUID id) {
        log.debug("Getting user: id={}", id);

        UserDto user = userService.findById(
            com.fabricmanagement.common.infrastructure.persistence.TenantContext.getCurrentTenantId(), 
            id
        ).orElseThrow(() -> new IllegalArgumentException("User not found"));

        return ResponseEntity.ok(ApiResponse.success(user));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserDto>>> getAllUsers() {
        log.debug("Getting all users");

        List<UserDto> users = userService.findByTenant(
            com.fabricmanagement.common.infrastructure.persistence.TenantContext.getCurrentTenantId()
        );

        return ResponseEntity.ok(ApiResponse.success(users));
    }

    @GetMapping("/company/{companyId}")
    public ResponseEntity<ApiResponse<List<UserDto>>> getUsersByCompany(@PathVariable UUID companyId) {
        log.debug("Getting users by company: companyId={}", companyId);

        List<UserDto> users = userService.findByCompany(
            com.fabricmanagement.common.infrastructure.persistence.TenantContext.getCurrentTenantId(),
            companyId
        );

        return ResponseEntity.ok(ApiResponse.success(users));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserDto>> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request) {
        log.info("Updating user: id={}", id);

        UserDto updated = userService.updateUser(id, request);

        return ResponseEntity.ok(ApiResponse.success(updated, "User updated successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivateUser(@PathVariable UUID id) {
        log.info("Deactivating user: id={}", id);

        userService.deactivateUser(id, "Deactivated by admin");

        return ResponseEntity.ok(ApiResponse.success(null, "User deactivated successfully"));
    }

    @GetMapping("/contact/{contactValue}")
    public ResponseEntity<ApiResponse<Boolean>> checkContactExists(@PathVariable String contactValue) {
        log.debug("Checking contact existence: {}", contactValue);

        boolean exists = userService.contactExists(contactValue);

        return ResponseEntity.ok(ApiResponse.success(exists));
    }
}

