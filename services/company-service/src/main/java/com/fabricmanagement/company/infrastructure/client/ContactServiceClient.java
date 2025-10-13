package com.fabricmanagement.company.infrastructure.client;

import com.fabricmanagement.shared.application.response.ApiResponse;
import com.fabricmanagement.company.infrastructure.client.dto.ContactDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Contact Service Client for Company Service
 *
 * Feign client for communicating with Contact Service.
 * 
 * ✅ LOOSE COUPLING:
 * - Includes fallback for graceful degradation
 * - Circuit breaker configured in application.yml
 * - Company operations continue even if Contact Service is down
 * 
 * URL: Configured via application.yml
 */
@FeignClient(
    name = "contact-service",
    url = "${contact-service.url:http://localhost:8082}",
    path = "/api/v1/contacts",
    configuration = com.fabricmanagement.shared.infrastructure.config.BaseFeignClientConfig.class,
    fallback = ContactServiceClientFallback.class  // ← RESILIENCE!
)
public interface ContactServiceClient {

    /**
     * Gets contacts by owner ID (Company ID)
     */
    @GetMapping("/owner/{ownerId}")
    ApiResponse<List<ContactDto>> getContactsByOwner(
        @PathVariable("ownerId") UUID ownerId,
        @RequestParam("ownerType") String ownerType
    );

    /**
     * Gets primary contact for a company
     */
    @GetMapping("/owner/{ownerId}/primary")
    ApiResponse<ContactDto> getPrimaryContact(
        @PathVariable("ownerId") UUID ownerId,
        @RequestParam("ownerType") String ownerType,
        @RequestParam("contactMedium") String contactMedium  // EMAIL, PHONE
    );

    /**
     * Gets contacts for multiple companies (batch operation)
     * 
     * Performance: N companies = 1 API call instead of N calls
     */
    @PostMapping("/batch/by-owners")
    ApiResponse<Map<String, List<ContactDto>>> getContactsByOwnersBatch(
        @RequestBody List<UUID> ownerIds,
        @RequestParam("ownerType") String ownerType
    );
    
    /**
     * Gets addresses for owner (Company)
     * 
     * @param ownerId Company ID
     * @param ownerType Owner type (COMPANY)
     * @return List of addresses
     */
    @GetMapping("/addresses/owner/{ownerId}")
    ApiResponse<List<com.fabricmanagement.company.infrastructure.client.dto.AddressDto>> getAddressesByOwner(
        @PathVariable("ownerId") UUID ownerId,
        @RequestParam("ownerType") String ownerType
    );
}

