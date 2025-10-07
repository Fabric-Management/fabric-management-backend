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
 * URL will be resolved via Service Discovery (future) or configuration.
 */
@FeignClient(
    name = "contact-service",
    url = "${contact-service.url:http://localhost:8082}",
    path = "/api/v1/contacts",
    configuration = com.fabricmanagement.user.infrastructure.config.FeignClientConfig.class
)
public interface ContactServiceClient {

    /**
     * Creates a new contact
     */
    @PostMapping
    ApiResponse<ContactDto> createContact(@RequestBody CreateContactDto request);

    /**
     * Gets contacts by owner ID
     */
    @GetMapping("/owner/{ownerId}")
    ApiResponse<List<ContactDto>> getContactsByOwner(@PathVariable("ownerId") String ownerId);

    /**
     * Gets verified contacts for an owner
     */
    @GetMapping("/owner/{ownerId}/verified")
    ApiResponse<List<ContactDto>> getVerifiedContacts(@PathVariable("ownerId") String ownerId);

    /**
     * Gets primary contact for an owner
     */
    @GetMapping("/owner/{ownerId}/primary")
    ApiResponse<ContactDto> getPrimaryContact(@PathVariable("ownerId") String ownerId);

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
     * Finds contacts by contact value (email or phone)
     */
    @GetMapping("/find-by-value")
    ApiResponse<List<ContactDto>> findByContactValue(@RequestParam("contactValue") String contactValue);
}
