package com.fabricmanagement.company.infrastructure.client;

import com.fabricmanagement.company.infrastructure.client.dto.UserDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * User Service Client for Company Service
 * 
 * Feign client for communicating with User Service
 */
@FeignClient(name = "user-service", url = "${user-service.url:http://localhost:8081}")
public interface UserServiceClient {
    
    /**
     * Gets a user by ID
     */
    @GetMapping("/api/v1/users/users/{userId}")
    UserDto getUser(@PathVariable("userId") UUID userId);
    
    /**
     * Gets users by company ID
     */
    @GetMapping("/api/v1/users/users/company/{companyId}")
    List<UserDto> getUsersByCompany(@PathVariable("companyId") UUID companyId);
    
    /**
     * Checks if a user exists
     */
    @GetMapping("/api/v1/users/users/{userId}/exists")
    boolean userExists(@PathVariable("userId") UUID userId);
    
    /**
     * Gets user count for a company
     */
    @GetMapping("/api/v1/users/users/company/{companyId}/count")
    int getUserCountForCompany(@PathVariable("companyId") UUID companyId);
}

