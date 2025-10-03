package com.fabricmanagement.company.infrastructure.client;

import com.fabricmanagement.company.infrastructure.client.dto.ContactDto;
import com.fabricmanagement.company.infrastructure.client.dto.CreateContactDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Contact Service Client for Company Service
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
     * Creates a new contact for a company
     */
    @PostMapping
    ContactDto createContact(@RequestBody CreateContactDto request);

    /**
     * Gets contacts by owner ID (company ID)
     */
    @GetMapping("/owner/{ownerId}")
    List<ContactDto> getContactsByOwner(@PathVariable("ownerId") String ownerId);

    /**
     * Gets verified contacts for a company
     */
    @GetMapping("/owner/{ownerId}/verified")
    List<ContactDto> getVerifiedContacts(@PathVariable("ownerId") String ownerId);

    /**
     * Gets primary contact for a company
     */
    @GetMapping("/owner/{ownerId}/primary")
    ContactDto getPrimaryContact(@PathVariable("ownerId") String ownerId);

    /**
     * Gets a specific contact
     */
    @GetMapping("/{contactId}")
    ContactDto getContact(@PathVariable("contactId") UUID contactId);

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
     * Sends verification code to a contact
     */
    @PostMapping("/{contactId}/send-verification")
    void sendVerificationCode(@PathVariable("contactId") UUID contactId);
}

