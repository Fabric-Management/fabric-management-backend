package com.fabricmanagement.user.infrastructure.web.controller;

import com.fabricmanagement.common.core.application.dto.ApiResponse;
import com.fabricmanagement.user.application.dto.contact.request.AddContactRequest;
import com.fabricmanagement.user.application.dto.contact.response.UserContactResponse;
import com.fabricmanagement.user.application.service.UserContactService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for user contact management operations.
 */
@RestController
@RequestMapping("/api/v1/users/contacts")
@RequiredArgsConstructor
@Tag(name = "User Contacts", description = "User contact management endpoints")
@Slf4j
public class UserContactController {

    private final UserContactService userContactService;

    /**
     * Gets all contacts for current user.
     */
    @GetMapping
    @Operation(summary = "Get user contacts", description = "Gets all contacts for current user")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER_ADMIN') or hasRole('USER')")
    public ResponseEntity<ApiResponse<List<UserContactResponse>>> getUserContacts(Authentication authentication) {
        log.info("Getting contacts for current user");
        
        // Get user ID from authentication context
        UUID userId = getCurrentUserId(authentication);
        
        List<UserContactResponse> response = userContactService.getUserContacts(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Adds a new contact to current user.
     */
    @PostMapping
    @Operation(summary = "Add user contact", description = "Adds a new contact to current user")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER_ADMIN') or hasRole('USER')")
    public ResponseEntity<ApiResponse<UserContactResponse>> addContact(
        @Valid @RequestBody AddContactRequest request,
        Authentication authentication
    ) {
        log.info("Adding contact for current user: {}", request.contactType());
        
        // Get user ID from authentication context
        UUID userId = getCurrentUserId(authentication);
        
        UserContactResponse response = userContactService.addUserContact(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(response, "Contact added successfully"));
    }

    /**
     * Removes a contact from current user.
     */
    @DeleteMapping("/{contactId}")
    @Operation(summary = "Remove user contact", description = "Removes a contact from current user")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER_ADMIN') or hasRole('USER')")
    public ResponseEntity<ApiResponse<Void>> removeContact(
        @PathVariable @Parameter(description = "Contact ID") UUID contactId,
        Authentication authentication
    ) {
        log.info("Removing contact for current user: {}", contactId);
        
        // Get user ID from authentication context
        UUID userId = getCurrentUserId(authentication);
        
        userContactService.removeUserContact(userId, contactId);
        return ResponseEntity.ok(ApiResponse.success(null, "Contact removed successfully"));
    }

    /**
     * Sets primary contact for current user.
     */
    @PutMapping("/{contactId}/primary")
    @Operation(summary = "Set primary contact", description = "Sets a contact as primary for current user")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER_ADMIN') or hasRole('USER')")
    public ResponseEntity<ApiResponse<Void>> setPrimaryContact(
        @PathVariable @Parameter(description = "Contact ID") UUID contactId,
        Authentication authentication
    ) {
        log.info("Setting primary contact for current user: {}", contactId);
        
        // Get user ID from authentication context
        UUID userId = getCurrentUserId(authentication);
        
        userContactService.setPrimaryContact(userId, contactId);
        return ResponseEntity.ok(ApiResponse.success(null, "Primary contact set successfully"));
    }

    /**
     * Verifies a contact for current user.
     */
    @PutMapping("/{contactId}/verify")
    @Operation(summary = "Verify contact", description = "Verifies a contact for current user")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER_ADMIN') or hasRole('USER')")
    public ResponseEntity<ApiResponse<Void>> verifyContact(
        @PathVariable @Parameter(description = "Contact ID") UUID contactId,
        Authentication authentication
    ) {
        log.info("Verifying contact for current user: {}", contactId);
        
        // Get user ID from authentication context
        UUID userId = getCurrentUserId(authentication);
        
        userContactService.verifyContact(userId, contactId);
        return ResponseEntity.ok(ApiResponse.success(null, "Contact verified successfully"));
    }

    /**
     * Gets primary contact for current user.
     */
    @GetMapping("/primary")
    @Operation(summary = "Get primary contact", description = "Gets primary contact for current user")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER_ADMIN') or hasRole('USER')")
    public ResponseEntity<ApiResponse<UserContactResponse>> getPrimaryContact(Authentication authentication) {
        log.info("Getting primary contact for current user");
        
        // Get user ID from authentication context
        UUID userId = getCurrentUserId(authentication);
        
        UserContactResponse response = userContactService.getPrimaryContact(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Helper method to get current user ID from authentication context.
     */
    private UUID getCurrentUserId(Authentication authentication) {
        // This would typically come from JWT token or security context
        // For now, returning a placeholder - implement based on your security setup
        return UUID.fromString(authentication.getName());
    }
}
