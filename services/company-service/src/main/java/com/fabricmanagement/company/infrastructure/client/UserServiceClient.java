package com.fabricmanagement.company.infrastructure.client;

import com.fabricmanagement.company.infrastructure.client.dto.UserDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * User Service Client for Company Service
 *
 * Feign client for communicating with User Service.
 * URL will be resolved via Service Discovery (future) or configuration.
 */
@FeignClient(
    name = "user-service",
    url = "${user-service.url:http://localhost:8081}",
    path = "/api/v1/users",
    configuration = com.fabricmanagement.company.infrastructure.config.FeignClientConfig.class
)
public interface UserServiceClient {

    /**
     * Gets a user by ID
     */
    @GetMapping("/{userId}")
    UserDto getUser(@PathVariable("userId") UUID userId);

    /**
     * Gets users by company ID
     */
    @GetMapping("/company/{companyId}")
    List<UserDto> getUsersByCompany(@PathVariable("companyId") UUID companyId);

    /**
     * Checks if a user exists
     */
    @GetMapping("/{userId}/exists")
    boolean userExists(@PathVariable("userId") UUID userId);

    /**
     * Gets user count for a company
     */
    @GetMapping("/company/{companyId}/count")
    int getUserCountForCompany(@PathVariable("companyId") UUID companyId);
}

