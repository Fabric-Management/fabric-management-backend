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
import java.util.Optional;
import java.util.UUID;

/**
 * Contact REST Controller
 * 
 * Provides API endpoints for contact management.
 * Follows Clean Architecture principles - only handles HTTP concerns.
 * 
 * API Version: v1
 * Base Path: / (Gateway strips /api/v1/contacts prefix)
 */
@RestController
@RequestMapping("/")
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
        // Note: ownerId is String in request DTO to handle both UUID and legacy formats
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
    public ResponseEntity<ApiResponse<List<ContactResponse>>> getContactsByOwner(@PathVariable UUID ownerId) {
        log.debug("Getting contacts for owner: {}", ownerId);
        
        // Validate access
        String currentUserId = SecurityContextHolder.getCurrentUserId();
        if (!currentUserId.equals(ownerId.toString()) && !SecurityContextHolder.hasRole("ADMIN")) {
            log.warn("Unauthorized contact access attempt by user {} for owner {}", currentUserId, ownerId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("You don't have permission to view these contacts", "FORBIDDEN"));
        }
        
        List<ContactResponse> contacts = contactService.getContactsByOwner(ownerId.toString());
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
    @PutMapping("/{contactId}/primary")
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
     * Sends verification code to a contact
     */
    @PostMapping("/{contactId}/send-verification")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> sendVerificationCode(@PathVariable UUID contactId) {
        log.info("Sending verification code to contact: {}", contactId);

        // Validate access
        ContactResponse existingContact = contactService.getContact(contactId);
        String currentUserId = SecurityContextHolder.getCurrentUserId();
        if (!currentUserId.equals(existingContact.getOwnerId()) && !SecurityContextHolder.hasRole("ADMIN")) {
            log.warn("Unauthorized send verification attempt by user {} for contact {}", currentUserId, contactId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("You don't have permission to send verification for this contact", "FORBIDDEN"));
        }

        contactService.sendVerificationCode(contactId);
        return ResponseEntity.ok(ApiResponse.success(null, "Verification code sent successfully"));
    }

    /**
     * Verifies a contact
     */
    @PutMapping("/{contactId}/verify")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> verifyContact(
            @PathVariable UUID contactId,
            @RequestParam String code) {
        
        log.info("Verifying contact: {}", contactId);
        
        // Validate access
        ContactResponse existingContact = contactService.getContact(contactId);
        String currentUserId = SecurityContextHolder.getCurrentUserId();
        if (!currentUserId.equals(existingContact.getOwnerId()) && !SecurityContextHolder.hasRole("ADMIN")) {
            log.warn("Unauthorized contact verification attempt by user {} for contact {}", currentUserId, contactId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("You don't have permission to verify this contact", "FORBIDDEN"));
        }

        contactService.verifyContact(contactId, code);
        return ResponseEntity.ok(ApiResponse.success(null, "Contact verified successfully"));
    }
    
    /**
     * Gets primary contact for an owner
     */
    @GetMapping("/owner/{ownerId}/primary")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ContactResponse>> getPrimaryContact(@PathVariable UUID ownerId) {
        log.debug("Getting primary contact for owner: {}", ownerId);
        
        // Validate access
        String currentUserId = SecurityContextHolder.getCurrentUserId();
        if (!currentUserId.equals(ownerId.toString()) && !SecurityContextHolder.hasRole("ADMIN")) {
            log.warn("Unauthorized contact access attempt by user {} for owner {}", currentUserId, ownerId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("You don't have permission to view these contacts", "FORBIDDEN"));
        }
        
        ContactResponse contact = contactService.getPrimaryContact(ownerId.toString());
        return ResponseEntity.ok(ApiResponse.success(contact));
    }
    
    /**
     * Checks if a contact value is available
     */
    @PostMapping("/check-availability")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Boolean>> checkAvailability(@RequestParam String contactValue) {
        log.debug("Checking availability for contact value: {}", contactValue);

        boolean available = contactService.checkAvailability(contactValue);
        return ResponseEntity.ok(ApiResponse.success(available));
    }

    /**
     * Searches contacts by type and owner
     */
    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ContactResponse>>> searchContacts(
            @RequestParam UUID ownerId,
            @RequestParam(required = false) String contactType) {

        log.debug("Searching contacts for owner: {} with type: {}", ownerId, contactType);

        // Validate access
        String currentUserId = SecurityContextHolder.getCurrentUserId();
        if (!currentUserId.equals(ownerId.toString()) && !SecurityContextHolder.hasRole("ADMIN")) {
            log.warn("Unauthorized contact search attempt by user {} for owner {}", currentUserId, ownerId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("You don't have permission to search these contacts", "FORBIDDEN"));
        }

        List<ContactResponse> contacts = contactService.searchContacts(ownerId.toString(), contactType);
        return ResponseEntity.ok(ApiResponse.success(contacts));
    }

    /**
     * Finds contact by contact value (email or phone)
     * Used for authentication purposes - no auth required
     * 
     * INTERNAL USE ONLY - Should only be called by User Service
     * Public access but with aggressive rate limiting in API Gateway
     * 
     * Security considerations:
     * - Email enumeration attack possible (mitigated by rate limiting + timing attack prevention in User Service)
     * - Response is minimal (no sensitive data exposed)
     * - Used only during authentication flow
     */
    @GetMapping("/find-by-value")
    public ResponseEntity<ApiResponse<ContactResponse>> findByContactValue(
            @RequestParam String contactValue) {

        log.debug("Finding contact by value: {}", contactValue);

        Optional<ContactResponse> contact = contactService.findByContactValue(contactValue);
        return contact
                .map(c -> ResponseEntity.ok(ApiResponse.success(c)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Contact not found", "CONTACT_NOT_FOUND")));
    }
}
