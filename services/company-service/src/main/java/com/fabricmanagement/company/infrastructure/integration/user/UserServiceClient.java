package com.fabricmanagement.company.infrastructure.integration.user;

import com.fabricmanagement.common.core.application.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Feign client for integrating with user-service.
 * Handles all communication with user-service for company-user relationships.
 *
 * Circuit breaker and resilience patterns will be applied via configuration.
 */
@FeignClient(
    name = "user-service",
    url = "${services.user-service.url:http://localhost:8081}",
    configuration = UserServiceClientConfiguration.class
)
public interface UserServiceClient {

    /**
     * Get user by ID.
     */
    @GetMapping("/api/v1/users/{userId}")
    ApiResponse<UserResponse> getUserById(@PathVariable UUID userId);

    /**
     * Get multiple users by IDs.
     */
    @PostMapping("/api/v1/users/batch")
    ApiResponse<List<UserResponse>> getUsersByIds(@RequestBody List<UUID> userIds);

    /**
     * Get users by company ID (users working for a company).
     */
    @GetMapping("/api/v1/users/company/{companyId}")
    ApiResponse<List<UserResponse>> getUsersByCompanyId(@PathVariable UUID companyId);

    /**
     * Check if user exists.
     */
    @GetMapping("/api/v1/users/{userId}/exists")
    ApiResponse<Boolean> userExists(@PathVariable UUID userId);

    /**
     * Get user basic info (minimal data for company-user relationships).
     */
    @GetMapping("/api/v1/users/{userId}/basic")
    ApiResponse<UserBasicResponse> getUserBasicInfo(@PathVariable UUID userId);

    /**
     * Get multiple users basic info.
     */
    @PostMapping("/api/v1/users/basic/batch")
    ApiResponse<List<UserBasicResponse>> getUsersBasicInfo(@RequestBody List<UUID> userIds);
}