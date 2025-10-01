package com.fabricmanagement.contact.api;

import com.fabricmanagement.contact.application.dto.*;
import com.fabricmanagement.contact.application.service.ContactService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

/**
 * Contact REST Controller
 * 
 * Provides API endpoints for contact management
 */
@RestController
@RequestMapping("/api/contacts")
@RequiredArgsConstructor
public class ContactController {
    
    private final ContactService contactService;
    
    /**
     * Creates a new contact
     */
    @PostMapping
    public ResponseEntity<ContactResponse> createContact(@Valid @RequestBody CreateContactRequest request) {
        ContactResponse response = contactService.createContact(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    /**
     * Gets contacts by owner ID
     */
    @GetMapping("/owner/{ownerId}")
    public ResponseEntity<List<ContactResponse>> getContactsByOwner(@PathVariable String ownerId) {
        List<ContactResponse> contacts = contactService.getContactsByOwner(ownerId);
        return ResponseEntity.ok(contacts);
    }
    
    /**
     * Gets a specific contact
     */
    @GetMapping("/{contactId}")
    public ResponseEntity<ContactResponse> getContact(@PathVariable UUID contactId) {
        ContactResponse contact = contactService.getContact(contactId);
        return ResponseEntity.ok(contact);
    }
    
    /**
     * Verifies a contact
     */
    @PutMapping("/{contactId}/verify")
    public ResponseEntity<ContactResponse> verifyContact(
            @PathVariable UUID contactId,
            @Valid @RequestBody VerifyContactRequest request) {
        ContactResponse response = contactService.verifyContact(contactId, request.getCode());
        return ResponseEntity.ok(response);
    }
    
    /**
     * Makes a contact primary
     */
    @PutMapping("/{contactId}/primary")
    public ResponseEntity<ContactResponse> makePrimary(@PathVariable UUID contactId) {
        ContactResponse response = contactService.makePrimary(contactId);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Deletes a contact
     */
    @DeleteMapping("/{contactId}")
    public ResponseEntity<Void> deleteContact(@PathVariable UUID contactId) {
        contactService.deleteContact(contactId);
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Checks if a contact value is available
     */
    @PostMapping("/check-availability")
    public ResponseEntity<ContactAvailabilityResponse> checkAvailability(
            @Valid @RequestBody CheckContactAvailabilityRequest request) {
        boolean available = contactService.isContactAvailable(request.getContactValue());
        return ResponseEntity.ok(new ContactAvailabilityResponse(request.getContactValue(), available));
    }
    
    /**
     * Sends verification code to a contact
     */
    @PostMapping("/{contactId}/send-verification")
    public ResponseEntity<Void> sendVerificationCode(@PathVariable UUID contactId) {
        contactService.sendVerificationCode(contactId);
        return ResponseEntity.ok().build();
    }
    
    /**
     * Gets verified contacts for an owner
     */
    @GetMapping("/owner/{ownerId}/verified")
    public ResponseEntity<List<ContactResponse>> getVerifiedContacts(@PathVariable String ownerId) {
        List<ContactResponse> contacts = contactService.getVerifiedContacts(ownerId);
        return ResponseEntity.ok(contacts);
    }
    
    /**
     * Gets primary contact for an owner
     */
    @GetMapping("/owner/{ownerId}/primary")
    public ResponseEntity<ContactResponse> getPrimaryContact(@PathVariable String ownerId) {
        ContactResponse contact = contactService.getPrimaryContact(ownerId);
        return ResponseEntity.ok(contact);
    }
}

