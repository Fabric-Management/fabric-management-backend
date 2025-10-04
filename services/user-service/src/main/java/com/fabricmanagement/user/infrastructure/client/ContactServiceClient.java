package com.fabricmanagement.user.infrastructure.client;

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
    path = "/api/v1/contacts"
)
public interface ContactServiceClient {

    /**
     * Creates a new contact
     */
    @PostMapping
    ContactDto createContact(@RequestBody CreateContactDto request);

    /**
     * Gets contacts by owner ID
     */
    @GetMapping("/owner/{ownerId}")
    List<ContactDto> getContactsByOwner(@PathVariable("ownerId") String ownerId);

    /**
     * Gets verified contacts for an owner
     */
    @GetMapping("/owner/{ownerId}/verified")
    List<ContactDto> getVerifiedContacts(@PathVariable("ownerId") String ownerId);

    /**
     * Gets primary contact for an owner
     */
    @GetMapping("/owner/{ownerId}/primary")
    ContactDto getPrimaryContact(@PathVariable("ownerId") String ownerId);

    /**
     * Verifies a contact
     */
    @PutMapping("/{contactId}/verify")
    ContactDto verifyContact(@PathVariable("contactId") UUID contactId, @RequestParam("code") String code);

    /**
     * Makes a contact primary
     */
    @PutMapping("/{contactId}/primary")
    ContactDto makePrimary(@PathVariable("contactId") UUID contactId);

    /**
     * Deletes a contact
     */
    @DeleteMapping("/{contactId}")
    void deleteContact(@PathVariable("contactId") UUID contactId);

    /**
     * Checks if a contact value is available
     */
    @PostMapping("/check-availability")
    boolean checkAvailability(@RequestParam("contactValue") String contactValue);

    /**
     * Sends verification code to a contact
     */
    @PostMapping("/{contactId}/send-verification")
    void sendVerificationCode(@PathVariable("contactId") UUID contactId);

    /**
     * Finds contacts by contact value (email or phone)
     */
    @GetMapping("/find-by-value")
    List<ContactDto> findByContactValue(@RequestParam("contactValue") String contactValue);
}
