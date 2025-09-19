package com.fabricmanagement.contact.infrastructure.integration.user;

import com.fabricmanagement.common.core.application.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Feign client for communication with user-service.
 * Handles all user-related queries from contact-service.
 */
@FeignClient(
    name = "user-service",
    url = "${services.user.url:http://localhost:8081}",
    configuration = UserServiceClientConfig.class
)
public interface UserServiceClient {

    /**
     * Gets a user by ID.
     * @param userId the user ID
     * @return the user DTO if found
     */
    @GetMapping("/api/v1/users/{userId}")
    Optional<UserDto> getUserById(@PathVariable("userId") UUID userId);

    /**
     * Gets a user by username.
     * @param username the username
     * @return the user DTO if found
     */
    @GetMapping("/api/v1/users/by-username")
    Optional<UserDto> getUserByUsername(@RequestParam("username") String username);

    /**
     * Gets a user by email.
     * @param email the email address
     * @return the user DTO if found
     */
    @GetMapping("/api/v1/users/by-email")
    Optional<UserDto> getUserByEmail(@RequestParam("email") String email);

    /**
     * Gets multiple users by IDs.
     * @param userIds list of user IDs
     * @return list of user DTOs
     */
    @GetMapping("/api/v1/users/batch")
    List<UserDto> getUsersByIds(@RequestParam("ids") List<UUID> userIds);

    /**
     * Validates if a user exists and is active.
     * @param userId the user ID
     * @return true if the user exists and is active
     */
    @GetMapping("/api/v1/users/{userId}/validate")
    boolean validateUser(@PathVariable("userId") UUID userId);

    /**
     * Gets users by tenant ID.
     * @param tenantId the tenant ID
     * @return list of users in the tenant
     */
    @GetMapping("/api/v1/users/by-tenant")
    List<UserDto> getUsersByTenant(@RequestParam("tenantId") UUID tenantId);

    /**
     * Checks if a user has a specific role.
     * @param userId the user ID
     * @param role the role to check
     * @return true if the user has the role
     */
    @GetMapping("/api/v1/users/{userId}/has-role")
    boolean userHasRole(@PathVariable("userId") UUID userId, @RequestParam("role") String role);

    /**
     * Gets the count of active users in a tenant.
     * @param tenantId the tenant ID
     * @return the count of active users
     */
    @GetMapping("/api/v1/users/count")
    Long getActiveUserCount(@RequestParam("tenantId") UUID tenantId);
}