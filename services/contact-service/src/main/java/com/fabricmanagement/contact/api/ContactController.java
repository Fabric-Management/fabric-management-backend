package com.fabricmanagement.contact.api;

import com.fabricmanagement.contact.application.dto.*;
import com.fabricmanagement.contact.application.service.ContactService;
import com.fabricmanagement.shared.application.context.SecurityContext;
import com.fabricmanagement.shared.application.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Contact REST Controller
 * 
 * Provides API endpoints for contact management.
 * Uses Spring Security's @AuthenticationPrincipal - 100% framework-native!
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
     * Lists all contacts (ADMIN only)
     * 
     * WARNING: Returns all contacts in the system. Use with caution.
     * For better performance, use /search endpoint with filters.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<ContactResponse>>> listAllContacts() {
        log.debug("Listing all contacts (ADMIN operation)");
        
        List<ContactResponse> contacts = contactService.listAllContacts();
        return ResponseEntity.ok(ApiResponse.success(contacts));
    }
    
    /**
     * Creates a new contact
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ContactResponse>> createContact(
            @Valid @RequestBody CreateContactRequest request,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.info("Creating contact for owner: {}", request.getOwnerId());
        
        // Validate access: users can only create contacts for themselves unless they're admin
        if (!ctx.getUserId().equals(request.getOwnerId()) && !ctx.hasRole("ADMIN")) {
            log.warn("Unauthorized contact creation attempt by user {} for owner {}", ctx.getUserId(), request.getOwnerId());
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
    public ResponseEntity<ApiResponse<List<ContactResponse>>> getContactsByOwner(
            @PathVariable UUID ownerId,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.debug("Getting contacts for owner: {}", ownerId);
        
        // Validate access
        if (!ctx.getUserId().equals(ownerId.toString()) && !ctx.hasRole("ADMIN")) {
            log.warn("Unauthorized contact access attempt by user {} for owner {}", ctx.getUserId(), ownerId);
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
    public ResponseEntity<ApiResponse<ContactResponse>> getContact(
            @PathVariable UUID contactId,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.debug("Getting contact: {}", contactId);
        
        ContactResponse contact = contactService.getContact(contactId);
        
        // Validate access
        if (!ctx.getUserId().equals(contact.getOwnerId()) && !ctx.hasRole("ADMIN")) {
            log.warn("Unauthorized contact access attempt by user {} for contact {}", ctx.getUserId(), contactId);
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
            @Valid @RequestBody UpdateContactRequest request,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.info("Updating contact: {}", contactId);
        
        // Validate access
        ContactResponse existingContact = contactService.getContact(contactId);
        if (!ctx.getUserId().equals(existingContact.getOwnerId()) && !ctx.hasRole("ADMIN")) {
            log.warn("Unauthorized contact update attempt by user {} for contact {}", ctx.getUserId(), contactId);
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
    public ResponseEntity<ApiResponse<Void>> deleteContact(
            @PathVariable UUID contactId,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.info("Deleting contact: {}", contactId);
        
        // Validate access
        ContactResponse existingContact = contactService.getContact(contactId);
        if (!ctx.getUserId().equals(existingContact.getOwnerId()) && !ctx.hasRole("ADMIN")) {
            log.warn("Unauthorized contact deletion attempt by user {} for contact {}", ctx.getUserId(), contactId);
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
    public ResponseEntity<ApiResponse<Void>> setPrimaryContact(
            @PathVariable UUID contactId,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.info("Setting contact as primary: {}", contactId);
        
        // Validate access
        ContactResponse existingContact = contactService.getContact(contactId);
        if (!ctx.getUserId().equals(existingContact.getOwnerId()) && !ctx.hasRole("ADMIN")) {
            log.warn("Unauthorized contact update attempt by user {} for contact {}", ctx.getUserId(), contactId);
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
    public ResponseEntity<ApiResponse<Void>> sendVerificationCode(
            @PathVariable UUID contactId,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.info("Sending verification code to contact: {}", contactId);

        // Validate access
        ContactResponse existingContact = contactService.getContact(contactId);
        if (!ctx.getUserId().equals(existingContact.getOwnerId()) && !ctx.hasRole("ADMIN")) {
            log.warn("Unauthorized send verification attempt by user {} for contact {}", ctx.getUserId(), contactId);
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
            @RequestParam String code,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.info("Verifying contact: {}", contactId);
        
        // Validate access
        ContactResponse existingContact = contactService.getContact(contactId);
        if (!ctx.getUserId().equals(existingContact.getOwnerId()) && !ctx.hasRole("ADMIN")) {
            log.warn("Unauthorized contact verification attempt by user {} for contact {}", ctx.getUserId(), contactId);
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
    public ResponseEntity<ApiResponse<ContactResponse>> getPrimaryContact(
            @PathVariable UUID ownerId,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.debug("Getting primary contact for owner: {}", ownerId);
        
        // Validate access
        if (!ctx.getUserId().equals(ownerId.toString()) && !ctx.hasRole("ADMIN")) {
            log.warn("Unauthorized contact access attempt by user {} for owner {}", ctx.getUserId(), ownerId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("You don't have permission to view these contacts", "FORBIDDEN"));
        }
        
        ContactResponse contact = contactService.getPrimaryContact(ownerId);
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
            @RequestParam(required = false) String contactType,
            @AuthenticationPrincipal SecurityContext ctx) {

        log.debug("Searching contacts for owner: {} with type: {}", ownerId, contactType);

        // Validate access
        if (!ctx.getUserId().equals(ownerId.toString()) && !ctx.hasRole("ADMIN")) {
            log.warn("Unauthorized contact search attempt by user {} for owner {}", ctx.getUserId(), ownerId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("You don't have permission to search these contacts", "FORBIDDEN"));
        }

        List<ContactResponse> contacts = contactService.searchContacts(ownerId, contactType);
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
    
    /**
     * Gets contacts for multiple owners (batch operation)
     * 
     * NEW ENDPOINT: Added for batch fetching to prevent N+1 query problem
     * 
     * INTERNAL USE ONLY - Should only be called by User Service
     * Public access for service-to-service communication
     * 
     * Performance: 100 users = 1 API call + 1 DB query instead of 100 calls + 100 queries
     * 
     * Request Body: [UUID, UUID, UUID, ...] as String array
     * Response: Map<UUID_as_String, List<ContactResponse>>
     * 
     * Example:
     * POST /api/v1/contacts/batch/by-owners
     * Body: ["123e4567-e89b-12d3-a456-426614174000", "223e4567-..."]
     * Response: {
     *   "123e4567-...": [{ "contactValue": "email@test.com", ... }],
     *   "223e4567-...": [{ "contactValue": "phone@test.com", ... }]
     * }
     */
    @PostMapping("/batch/by-owners")
    public ResponseEntity<ApiResponse<java.util.Map<String, List<ContactResponse>>>> getContactsByOwnersBatch(
            @RequestBody List<UUID> ownerIds) {
        
        log.info("Batch fetching contacts for {} owners", ownerIds != null ? ownerIds.size() : 0);
        
        if (ownerIds == null || ownerIds.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success(java.util.Collections.emptyMap()));
        }
        
        java.util.Map<UUID, List<ContactResponse>> contactsMap = 
            contactService.getContactsByOwnersBatch(ownerIds);
        
        // Convert UUID keys to String for API response compatibility
        java.util.Map<String, List<ContactResponse>> stringKeyMap = contactsMap.entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(
                entry -> entry.getKey().toString(),
                java.util.Map.Entry::getValue
            ));
        
        return ResponseEntity.ok(ApiResponse.success(stringKeyMap, 
            "Batch contacts retrieved successfully for " + ownerIds.size() + " owners"));
    }
}
