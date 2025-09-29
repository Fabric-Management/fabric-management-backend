package com.fabricmanagement.identity.infrastructure.integration.user;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * Single Responsibility: User Service HTTP communication only
 * Interface Segregation: Only necessary HTTP endpoints
 * Dependency Inversion: Depends on abstraction, not implementation
 */
@FeignClient(name = "user-service", url = "${feign.client.config.user-service.url}", fallback = UserServiceFallback.class)
public interface UserServiceClient {

    @PostMapping("/api/v1/users/profile")
    UserProfileResponse createUserProfile(@RequestBody CreateUserProfileRequest request);

    @PutMapping("/api/v1/users/{userId}/identity")
    UserProfileResponse updateUserProfileIdentity(@PathVariable String userId, @RequestBody UpdateUserProfileIdentityRequest request);

    @PutMapping("/api/v1/users/{userId}/status")
    UserProfileResponse updateUserProfileStatus(@PathVariable String userId, @RequestBody UpdateUserProfileStatusRequest request);

    @GetMapping("/api/v1/users/{userId}/profile")
    UserProfileResponse getUserProfile(@PathVariable String userId);

    // Request DTOs
    record CreateUserProfileRequest(
        String id,
        String tenantId,
        String username,
        String email
    ) {}

    record UpdateUserProfileIdentityRequest(
        String username,
        String email
    ) {}

    record UpdateUserProfileStatusRequest(
        String status
    ) {}

    // Response DTO
    record UserProfileResponse(
        String id,
        String tenantId,
        String username,
        String email,
        String firstName,
        String lastName,
        String status
    ) {}
}