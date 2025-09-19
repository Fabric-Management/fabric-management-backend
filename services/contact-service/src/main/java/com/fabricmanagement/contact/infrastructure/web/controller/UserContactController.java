package com.fabricmanagement.contact.infrastructure.web.controller;

import com.fabricmanagement.common.core.application.dto.ApiResponse;
import com.fabricmanagement.common.core.application.dto.PageRequest;
import com.fabricmanagement.common.core.application.dto.PageResponse;
import com.fabricmanagement.contact.application.dto.contact.request.CreateContactRequest;
import com.fabricmanagement.contact.application.dto.contact.request.UpdateContactRequest;
import com.fabricmanagement.contact.application.dto.contact.response.ContactDetailResponse;
import com.fabricmanagement.contact.application.dto.contact.response.ContactResponse;
import com.fabricmanagement.contact.application.service.UserContactService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for user contact operations.
 */
@RestController
@RequestMapping("/api/v1/contacts/users")
@RequiredArgsConstructor
@Tag(name = "User Contacts", description = "User contact management endpoints")
@Slf4j
public class UserContactController {

    private final UserContactService userContactService;

    /**
     * Creates a new contact for a user.
     */
    @PostMapping("/{userId}")
    @Operation(summary = "Create user contact", description = "Creates a new contact for the specified user")
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal.userId")
    public ResponseEntity<ApiResponse<ContactDetailResponse>> createUserContact(
        @PathVariable @Parameter(description = "User ID") UUID userId,
        @Valid @RequestBody CreateContactRequest request
    ) {
        log.info("Creating contact for user: {}", userId);
        ContactDetailResponse response = userContactService.createUserContact(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(response, "User contact created successfully"));
    }

    /**
     * Gets a user's contact by user ID.
     */
    @GetMapping("/{userId}")
    @Operation(summary = "Get user contact", description = "Gets the contact information for the specified user")
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal.userId")
    public ResponseEntity<ApiResponse<ContactDetailResponse>> getUserContact(
        @PathVariable @Parameter(description = "User ID") UUID userId
    ) {
        log.info("Fetching contact for user: {}", userId);
        ContactDetailResponse response = userContactService.getUserContact(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Updates a user's contact.
     */
    @PutMapping("/{userId}")
    @Operation(summary = "Update user contact", description = "Updates the contact information for the specified user")
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal.userId")
    public ResponseEntity<ApiResponse<ContactDetailResponse>> updateUserContact(
        @PathVariable @Parameter(description = "User ID") UUID userId,
        @Valid @RequestBody UpdateContactRequest request
    ) {
        log.info("Updating contact for user: {}", userId);
        ContactDetailResponse response = userContactService.updateUserContact(userId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "User contact updated successfully"));
    }

    /**
     * Deletes a user's contact.
     */
    @DeleteMapping("/{userId}")
    @Operation(summary = "Delete user contact", description = "Deletes the contact information for the specified user")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteUserContact(
        @PathVariable @Parameter(description = "User ID") UUID userId
    ) {
        log.info("Deleting contact for user: {}", userId);
        userContactService.deleteUserContact(userId);
        return ResponseEntity.ok(ApiResponse.success(null, "User contact deleted successfully"));
    }

    /**
     * Gets all user contacts for the current tenant.
     */
    @GetMapping
    @Operation(summary = "List user contacts", description = "Gets all user contacts with pagination")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<ContactResponse>>> getUserContacts(
        @Parameter(description = "Tenant ID") @RequestParam(required = false) UUID tenantId,
        @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
        @Parameter(description = "Sort by") @RequestParam(defaultValue = "createdAt") String sortBy,
        @Parameter(description = "Sort direction") @RequestParam(defaultValue = "DESC") String sortDirection
    ) {
        log.info("Fetching user contacts - page: {}, size: {}", page, size);

        PageRequest pageRequest = PageRequest.of(page, size)
            .withSort(sortBy, sortDirection);

        PageResponse<ContactResponse> response = userContactService.getUserContactsByTenant(
            tenantId != null ? tenantId : getCurrentTenantId(),
            pageRequest
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Searches user contacts.
     */
    @GetMapping("/search")
    @Operation(summary = "Search user contacts", description = "Searches user contacts by query")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<ContactResponse>>> searchUserContacts(
        @Parameter(description = "Search query") @RequestParam String query,
        @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size
    ) {
        log.info("Searching user contacts with query: {}", query);

        PageRequest pageRequest = PageRequest.of(page, size);
        PageResponse<ContactResponse> response = userContactService.searchUserContacts(query, pageRequest);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Updates emergency contact for a user.
     */
    @PutMapping("/{userId}/emergency")
    @Operation(summary = "Update emergency contact", description = "Updates the emergency contact information for the specified user")
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal.userId")
    public ResponseEntity<ApiResponse<ContactDetailResponse>> updateEmergencyContact(
        @PathVariable @Parameter(description = "User ID") UUID userId,
        @Parameter(description = "Emergency contact name") @RequestParam String name,
        @Parameter(description = "Emergency contact phone") @RequestParam String phone,
        @Parameter(description = "Relationship") @RequestParam String relationship
    ) {
        log.info("Updating emergency contact for user: {}", userId);
        ContactDetailResponse response = userContactService.updateEmergencyContact(userId, name, phone, relationship);
        return ResponseEntity.ok(ApiResponse.success(response, "Emergency contact updated successfully"));
    }

    /**
     * Gets the current tenant ID from security context.
     * This is a placeholder - implement based on your security setup.
     */
    private UUID getCurrentTenantId() {
        // TODO: Get from security context
        return UUID.randomUUID();
    }
}