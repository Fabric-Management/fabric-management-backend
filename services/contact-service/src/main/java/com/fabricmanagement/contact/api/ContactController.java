package com.fabricmanagement.contact.api;

import com.fabricmanagement.contact.application.dto.*;
import com.fabricmanagement.contact.application.service.ContactService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

/**
 * Contact REST Controller
 * 
 * Provides API endpoints for contact management with security controls
 */
@RestController
@RequestMapping("/api/contacts")
@RequiredArgsConstructor
@Slf4j
public class ContactController {
    
    private final ContactService contactService;
    
    /**
     * Gets the current authenticated user/company ID from security context
     */
    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        return null;
    }
    
    /**
     * Checks if the current user has access to the owner's contacts
     */
    private boolean hasAccessToOwner(String ownerId) {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            return false;
        }
        // Admin users have access to all contacts
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_SYSTEM"))) {
            return true;
        }
        // Regular users can only access their own contacts
        return currentUserId.equals(ownerId);
    }
    
    /**
     * Creates a new contact
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ContactResponse> createContact(@Valid @RequestBody CreateContactRequest request) {
        // Validate that the user can only create contacts for themselves (unless admin)
        if (!hasAccessToOwner(request.getOwnerId())) {
            log.warn("Unauthorized contact creation attempt by user {} for owner {}", getCurrentUserId(), request.getOwnerId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        ContactResponse response = contactService.createContact(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    /**
     * Gets contacts by owner ID
     */
    @GetMapping("/owner/{ownerId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ContactResponse>> getContactsByOwner(@PathVariable String ownerId) {
        // Validate access to owner's contacts
        if (!hasAccessToOwner(ownerId)) {
            log.warn("Unauthorized access attempt by user {} to contacts of owner {}", getCurrentUserId(), ownerId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        List<ContactResponse> contacts = contactService.getContactsByOwner(ownerId);
        return ResponseEntity.ok(contacts);
    }
    
    /**
     * Gets a specific contact
     */
    @GetMapping("/{contactId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ContactResponse> getContact(@PathVariable UUID contactId) {
        ContactResponse contact = contactService.getContact(contactId);
        
        // Validate access to this contact
        if (!hasAccessToOwner(contact.getOwnerId())) {
            log.warn("Unauthorized access attempt by user {} to contact {}", getCurrentUserId(), contactId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        return ResponseEntity.ok(contact);
    }
    
    /**
     * Verifies a contact
     */
    @PutMapping("/{contactId}/verify")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ContactResponse> verifyContact(
            @PathVariable UUID contactId,
            @Valid @RequestBody VerifyContactRequest request) {
        // First get the contact to check ownership
        ContactResponse contact = contactService.getContact(contactId);
        if (!hasAccessToOwner(contact.getOwnerId())) {
            log.warn("Unauthorized verification attempt by user {} for contact {}", getCurrentUserId(), contactId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        ContactResponse response = contactService.verifyContact(contactId, request.getCode());
        return ResponseEntity.ok(response);
    }
    
    /**
     * Makes a contact primary
     */
    @PutMapping("/{contactId}/primary")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ContactResponse> makePrimary(@PathVariable UUID contactId) {
        // First get the contact to check ownership
        ContactResponse contact = contactService.getContact(contactId);
        if (!hasAccessToOwner(contact.getOwnerId())) {
            log.warn("Unauthorized primary change attempt by user {} for contact {}", getCurrentUserId(), contactId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        ContactResponse response = contactService.makePrimary(contactId);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Deletes a contact
     */
    @DeleteMapping("/{contactId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteContact(@PathVariable UUID contactId) {
        // First get the contact to check ownership
        ContactResponse contact = contactService.getContact(contactId);
        if (!hasAccessToOwner(contact.getOwnerId())) {
            log.warn("Unauthorized delete attempt by user {} for contact {}", getCurrentUserId(), contactId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        contactService.deleteContact(contactId);
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Checks if a contact value is available
     * Public endpoint for registration flow
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
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> sendVerificationCode(@PathVariable UUID contactId) {
        // First get the contact to check ownership
        ContactResponse contact = contactService.getContact(contactId);
        if (!hasAccessToOwner(contact.getOwnerId())) {
            log.warn("Unauthorized send verification attempt by user {} for contact {}", getCurrentUserId(), contactId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        contactService.sendVerificationCode(contactId);
        return ResponseEntity.ok().build();
    }
    
    /**
     * Gets verified contacts for an owner
     */
    @GetMapping("/owner/{ownerId}/verified")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ContactResponse>> getVerifiedContacts(@PathVariable String ownerId) {
        // Validate access to owner's contacts
        if (!hasAccessToOwner(ownerId)) {
            log.warn("Unauthorized access attempt by user {} to verified contacts of owner {}", getCurrentUserId(), ownerId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        List<ContactResponse> contacts = contactService.getVerifiedContacts(ownerId);
        return ResponseEntity.ok(contacts);
    }
    
    /**
     * Gets primary contact for an owner
     */
    @GetMapping("/owner/{ownerId}/primary")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ContactResponse> getPrimaryContact(@PathVariable String ownerId) {
        // Validate access to owner's contacts
        if (!hasAccessToOwner(ownerId)) {
            log.warn("Unauthorized access attempt by user {} to primary contact of owner {}", getCurrentUserId(), ownerId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        ContactResponse contact = contactService.getPrimaryContact(ownerId);
        return ResponseEntity.ok(contact);
    }
}

