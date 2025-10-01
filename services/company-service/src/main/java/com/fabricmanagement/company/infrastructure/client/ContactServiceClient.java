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
 * Feign client for communicating with Contact Service
 */
@FeignClient(name = "contact-service", url = "${contact-service.url:http://localhost:8082}")
public interface ContactServiceClient {
    
    /**
     * Creates a new contact for a company
     */
    @PostMapping("/api/v1/contacts/contacts")
    ContactDto createContact(@RequestBody CreateContactDto request);
    
    /**
     * Gets contacts by owner ID (company ID)
     */
    @GetMapping("/api/v1/contacts/contacts/owner/{ownerId}")
    List<ContactDto> getContactsByOwner(@PathVariable("ownerId") String ownerId);
    
    /**
     * Gets verified contacts for a company
     */
    @GetMapping("/api/v1/contacts/contacts/owner/{ownerId}/verified")
    List<ContactDto> getVerifiedContacts(@PathVariable("ownerId") String ownerId);
    
    /**
     * Gets primary contact for a company
     */
    @GetMapping("/api/v1/contacts/contacts/owner/{ownerId}/primary")
    ContactDto getPrimaryContact(@PathVariable("ownerId") String ownerId);
    
    /**
     * Gets a specific contact
     */
    @GetMapping("/api/v1/contacts/contacts/{contactId}")
    ContactDto getContact(@PathVariable("contactId") UUID contactId);
    
    /**
     * Verifies a contact
     */
    @PutMapping("/api/v1/contacts/contacts/{contactId}/verify")
    ContactDto verifyContact(@PathVariable("contactId") UUID contactId, @RequestParam("code") String code);
    
    /**
     * Makes a contact primary
     */
    @PutMapping("/api/v1/contacts/contacts/{contactId}/primary")
    ContactDto makePrimary(@PathVariable("contactId") UUID contactId);
    
    /**
     * Deletes a contact
     */
    @DeleteMapping("/api/v1/contacts/contacts/{contactId}")
    void deleteContact(@PathVariable("contactId") UUID contactId);
    
    /**
     * Sends verification code to a contact
     */
    @PostMapping("/api/v1/contacts/contacts/{contactId}/send-verification")
    void sendVerificationCode(@PathVariable("contactId") UUID contactId);
}

