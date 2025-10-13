package com.fabricmanagement.user.infrastructure.client;

import com.fabricmanagement.shared.application.response.ApiResponse;
import com.fabricmanagement.user.infrastructure.client.dto.ContactDto;
import com.fabricmanagement.user.infrastructure.client.dto.CreateContactDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Contact Service Client
 *
 * Feign client for communicating with Contact Service.
 * 
 * ✅ LOOSE COUPLING:
 * - Includes fallback for graceful degradation
 * - Circuit breaker configured in application.yml
 * - User operations continue even if Contact Service is down
 * 
 * URL: Configured via application.yml or service discovery
 */
@FeignClient(
    name = "contact-service",
    url = "${CONTACT_SERVICE_URL:http://localhost:8082}",
    path = "/api/v1/contacts",
    configuration = com.fabricmanagement.shared.infrastructure.config.BaseFeignClientConfig.class,
    fallback = ContactServiceClientFallback.class  // ← RESILIENCE!
)
public interface ContactServiceClient {

    /**
     * Gets contacts by owner ID
     */
    @GetMapping("/owner/{ownerId}")
    ApiResponse<List<ContactDto>> getContactsByOwner(@PathVariable("ownerId") UUID ownerId);

    /**
     * Gets verified contacts for an owner
     */
    @GetMapping("/owner/{ownerId}/verified")
    ApiResponse<List<ContactDto>> getVerifiedContacts(@PathVariable("ownerId") UUID ownerId);

    /**
     * Gets primary contact for an owner
     */
    @GetMapping("/owner/{ownerId}/primary")
    ApiResponse<ContactDto> getPrimaryContact(@PathVariable("ownerId") UUID ownerId);

    /**
     * Verifies a contact
     */
    @PutMapping("/{contactId}/verify")
    ApiResponse<ContactDto> verifyContact(@PathVariable("contactId") UUID contactId, @RequestParam("code") String code);

    /**
     * Makes a contact primary
     */
    @PutMapping("/{contactId}/primary")
    ApiResponse<ContactDto> makePrimary(@PathVariable("contactId") UUID contactId);

    /**
     * Deletes a contact
     */
    @DeleteMapping("/{contactId}")
    ApiResponse<Void> deleteContact(@PathVariable("contactId") UUID contactId);

    /**
     * Checks if a contact value is available
     */
    @PostMapping("/check-availability")
    ApiResponse<Boolean> checkAvailability(@RequestParam("contactValue") String contactValue);

    /**
     * Sends verification code to a contact
     */
    @PostMapping("/{contactId}/send-verification")
    ApiResponse<Void> sendVerificationCode(@PathVariable("contactId") UUID contactId);

    /**
     * Finds contact by contact value (email or phone)
     * Returns single contact since contact_value is UNIQUE
     */
    @GetMapping("/find-by-value")
    ApiResponse<ContactDto> findByContactValue(@RequestParam("contactValue") String contactValue);
    
    /**
     * Gets contacts for multiple owners (batch operation)
     * 
     * NEW: Added to prevent N+1 query problem
     * Performance: 100 users = 1 API call instead of 100 calls
     * 
     * Request: List of UUID owner IDs
     * Response: Map<String, List<ContactDto>> (String key for JSON compatibility)
     */
    @PostMapping("/batch/by-owners")
    ApiResponse<java.util.Map<String, List<ContactDto>>> getContactsByOwnersBatch(@RequestBody List<UUID> ownerIds);
    
    @PostMapping
    ApiResponse<ContactDto> createContact(@RequestBody CreateContactDto request);
    
    /**
     * Check if email domain is already registered
     * 
     * Used during tenant onboarding to detect if company email domain
     * already exists in the system (possible duplicate company or colleague)
     * 
     * @param emailDomain Email domain (e.g., "acmetekstil.com")
     * @return List of owner IDs that use this email domain
     */
    @GetMapping("/check-domain")
    ApiResponse<List<UUID>> checkEmailDomain(@RequestParam("domain") String emailDomain);
    
    /**
     * Creates address for owner (Company or User)
     * 
     * @param request Address creation request
     * @return Address response with ID
     */
    @PostMapping("/addresses")
    ApiResponse<com.fabricmanagement.user.infrastructure.client.dto.AddressDto> createAddress(
        @RequestBody com.fabricmanagement.user.infrastructure.client.dto.CreateAddressDto request);
    
    /**
     * Gets addresses for owner
     * 
     * @param ownerId Owner ID (Company or User)
     * @param ownerType Owner type (COMPANY or USER)
     * @return List of addresses
     */
    @GetMapping("/addresses/owner/{ownerId}")
    ApiResponse<List<com.fabricmanagement.user.infrastructure.client.dto.AddressDto>> getAddressesByOwner(
        @PathVariable("ownerId") UUID ownerId,
        @RequestParam("ownerType") String ownerType);
}
