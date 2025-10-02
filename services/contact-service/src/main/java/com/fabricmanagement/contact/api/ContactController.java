package com.fabricmanagement.contact.api;

import com.fabricmanagement.contact.application.dto.*;
import com.fabricmanagement.contact.application.service.ContactService;
import com.fabricmanagement.shared.application.response.ApiResponse;
import com.fabricmanagement.shared.infrastructure.security.SecurityContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

/**
 * Contact REST Controller
 * 
 * Provides API endpoints for contact management.
 * Follows Clean Architecture principles - only handles HTTP concerns.
 * 
 * API Version: v1
 * Base Path: /api/v1/contacts
 */
@RestController
@RequestMapping("/api/v1/contacts")
@RequiredArgsConstructor
@Slf4j
public class ContactController {
    
    private final ContactService contactService;
    
    /**
     * Creates a new contact
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ContactResponse>> createContact(@Valid @RequestBody CreateContactRequest request) {
        log.info("Creating contact for owner: {}", request.getOwnerId());
        
        // Validate access: users can only create contacts for themselves unless they're admin
        String currentUserId = SecurityContextHolder.getCurrentUserId();
        if (!currentUserId.equals(request.getOwnerId()) && !SecurityContextHolder.hasRole("ADMIN")) {
            log.warn("Unauthorized contact creation attempt by user {} for owner {}", currentUserId, request.getOwnerId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("You don't have permission to create contacts for this owner", "FORBIDDEN"));
        }
        
        ContactResponse response = contactService.createContact(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Contact created successfully"));
    }
    
    /**
     * Gets contacts by owner ID
     */
    @GetMapping("/owner/{ownerId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ContactResponse>>> getContactsByOwner(@PathVariable String ownerId) {
        log.debug("Getting contacts for owner: {}", ownerId);
        
        // Validate access
        String currentUserId = SecurityContextHolder.getCurrentUserId();
        if (!currentUserId.equals(ownerId) && !SecurityContextHolder.hasRole("ADMIN")) {
            log.warn("Unauthorized contact access attempt by user {} for owner {}", currentUserId, ownerId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("You don't have permission to view these contacts", "FORBIDDEN"));
        }
        
        List<ContactResponse> contacts = contactService.getContactsByOwner(ownerId);
        return ResponseEntity.ok(ApiResponse.success(contacts));
    }
    
    /**
     * Gets a contact by ID
     */
    @GetMapping("/{contactId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ContactResponse>> getContact(@PathVariable UUID contactId) {
        log.debug("Getting contact: {}", contactId);
        
        ContactResponse contact = contactService.getContact(contactId);
        
        // Validate access
        String currentUserId = SecurityContextHolder.getCurrentUserId();
        if (!currentUserId.equals(contact.getOwnerId()) && !SecurityContextHolder.hasRole("ADMIN")) {
            log.warn("Unauthorized contact access attempt by user {} for contact {}", currentUserId, contactId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("You don't have permission to view this contact", "FORBIDDEN"));
        }
        
        return ResponseEntity.ok(ApiResponse.success(contact));
    }
    
    /**
     * Updates a contact
     */
    @PutMapping("/{contactId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> updateContact(
            @PathVariable UUID contactId,
            @Valid @RequestBody UpdateContactRequest request) {
        
        log.info("Updating contact: {}", contactId);
        
        // Validate access
        ContactResponse existingContact = contactService.getContact(contactId);
        String currentUserId = SecurityContextHolder.getCurrentUserId();
        if (!currentUserId.equals(existingContact.getOwnerId()) && !SecurityContextHolder.hasRole("ADMIN")) {
            log.warn("Unauthorized contact update attempt by user {} for contact {}", currentUserId, contactId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("You don't have permission to update this contact", "FORBIDDEN"));
        }
        
        contactService.updateContact(contactId, request);
        return ResponseEntity.ok(ApiResponse.success(null, "Contact updated successfully"));
    }
    
    /**
     * Deletes a contact (soft delete)
     */
    @DeleteMapping("/{contactId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deleteContact(@PathVariable UUID contactId) {
        log.info("Deleting contact: {}", contactId);
        
        // Validate access
        ContactResponse existingContact = contactService.getContact(contactId);
        String currentUserId = SecurityContextHolder.getCurrentUserId();
        if (!currentUserId.equals(existingContact.getOwnerId()) && !SecurityContextHolder.hasRole("ADMIN")) {
            log.warn("Unauthorized contact deletion attempt by user {} for contact {}", currentUserId, contactId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("You don't have permission to delete this contact", "FORBIDDEN"));
        }
        
        contactService.deleteContact(contactId);
        return ResponseEntity.ok(ApiResponse.success(null, "Contact deleted successfully"));
    }
    
    /**
     * Sets a contact as primary
     */
    @PostMapping("/{contactId}/set-primary")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> setPrimaryContact(@PathVariable UUID contactId) {
        log.info("Setting contact as primary: {}", contactId);
        
        // Validate access
        ContactResponse existingContact = contactService.getContact(contactId);
        String currentUserId = SecurityContextHolder.getCurrentUserId();
        if (!currentUserId.equals(existingContact.getOwnerId()) && !SecurityContextHolder.hasRole("ADMIN")) {
            log.warn("Unauthorized contact update attempt by user {} for contact {}", currentUserId, contactId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("You don't have permission to update this contact", "FORBIDDEN"));
        }
        
        contactService.setPrimaryContact(contactId);
        return ResponseEntity.ok(ApiResponse.success(null, "Contact set as primary successfully"));
    }
    
    /**
     * Verifies a contact
     */
    @PostMapping("/{contactId}/verify")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> verifyContact(
            @PathVariable UUID contactId,
            @RequestParam String verificationCode) {
        
        log.info("Verifying contact: {}", contactId);
        
        // Validate access
        ContactResponse existingContact = contactService.getContact(contactId);
        String currentUserId = SecurityContextHolder.getCurrentUserId();
        if (!currentUserId.equals(existingContact.getOwnerId()) && !SecurityContextHolder.hasRole("ADMIN")) {
            log.warn("Unauthorized contact verification attempt by user {} for contact {}", currentUserId, contactId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("You don't have permission to verify this contact", "FORBIDDEN"));
        }
        
        contactService.verifyContact(contactId, verificationCode);
        return ResponseEntity.ok(ApiResponse.success(null, "Contact verified successfully"));
    }
    
    /**
     * Gets primary contact for an owner
     */
    @GetMapping("/owner/{ownerId}/primary")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ContactResponse>> getPrimaryContact(@PathVariable String ownerId) {
        log.debug("Getting primary contact for owner: {}", ownerId);
        
        // Validate access
        String currentUserId = SecurityContextHolder.getCurrentUserId();
        if (!currentUserId.equals(ownerId) && !SecurityContextHolder.hasRole("ADMIN")) {
            log.warn("Unauthorized contact access attempt by user {} for owner {}", currentUserId, ownerId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("You don't have permission to view these contacts", "FORBIDDEN"));
        }
        
        ContactResponse contact = contactService.getPrimaryContact(ownerId);
        return ResponseEntity.ok(ApiResponse.success(contact));
    }
    
    /**
     * Searches contacts by type and owner
     */
    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ContactResponse>>> searchContacts(
            @RequestParam String ownerId,
            @RequestParam(required = false) String contactType) {
        
        log.debug("Searching contacts for owner: {} with type: {}", ownerId, contactType);
        
        // Validate access
        String currentUserId = SecurityContextHolder.getCurrentUserId();
        if (!currentUserId.equals(ownerId) && !SecurityContextHolder.hasRole("ADMIN")) {
            log.warn("Unauthorized contact search attempt by user {} for owner {}", currentUserId, ownerId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("You don't have permission to search these contacts", "FORBIDDEN"));
        }
        
        List<ContactResponse> contacts = contactService.searchContacts(ownerId, contactType);
        return ResponseEntity.ok(ApiResponse.success(contacts));
    }
}
