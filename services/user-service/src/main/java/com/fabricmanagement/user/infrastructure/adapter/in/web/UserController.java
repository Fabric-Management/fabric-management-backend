package com.fabricmanagement.user.infrastructure.adapter.in.web;

import com.fabricmanagement.common.web.dto.ApiResponse;
import com.fabricmanagement.user.application.dto.query.UserResponse;
import com.fabricmanagement.user.infrastructure.adapter.in.web.dto.request.CreateUserRequest;
import com.fabricmanagement.user.infrastructure.adapter.in.web.dto.request.UpdateUserRequest;
import com.fabricmanagement.user.infrastructure.adapter.in.web.facade.UserFacadeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserFacadeService userFacadeService;

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> createUser(
            @Valid @RequestBody CreateUserRequest request,
            @RequestHeader("X-Tenant-ID") UUID tenantId) {

        UserResponse response = userFacadeService.createUser(request, tenantId);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "User created successfully"));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(
            @PathVariable UUID userId,
            @RequestHeader("X-Tenant-ID") UUID tenantId) {

        UserResponse response = userFacadeService.getUser(userId, tenantId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserRequest request,
            @RequestHeader("X-Tenant-ID") UUID tenantId) {

        UserResponse response = userFacadeService.updateUser(userId, request, tenantId);
        return ResponseEntity.ok(ApiResponse.success(response, "User updated successfully"));
    }
}