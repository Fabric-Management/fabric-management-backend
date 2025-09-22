package com.fabricmanagement.contact.infrastructure.web.controller;

import com.fabricmanagement.common.core.application.dto.ApiResponse;
import com.fabricmanagement.common.security.context.SecurityContextUtil;
import com.fabricmanagement.contact.application.dto.common.PageRequestDto;
import com.fabricmanagement.common.core.application.dto.PageResponse;
import com.fabricmanagement.contact.application.dto.usercontact.request.CreateUserContactRequest;
import com.fabricmanagement.contact.application.dto.usercontact.request.UpdateUserContactRequest;
import com.fabricmanagement.contact.application.dto.usercontact.response.UserContactResponse;
import com.fabricmanagement.contact.application.dto.usercontact.response.UserContactListResponse;
import com.fabricmanagement.contact.application.service.UserContactService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for user contact operations.
 */
@RestController
@RequestMapping("/api/v1/contacts/users")
@RequiredArgsConstructor
@Tag(name = "User Contacts", description = "User contact management endpoints")
@Slf4j
@Validated
public class UserContactController {

    private final UserContactService userContactService;

    /**
     * Creates a new contact for a user.
     */
    @PostMapping("/{userId}")
    @Operation(summary = "Create user contact", description = "Creates a new contact for the specified user")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<ApiResponse<UserContactResponse>> createUserContact(
        @PathVariable @Parameter(description = "User ID") UUID userId,
        @Valid @RequestBody CreateUserContactRequest request
    ) {
        log.info("Creating contact for user: {}", userId);

        // Ensure the userId in path matches the one in request
        if (!userId.equals(request.userId())) {
            throw new IllegalArgumentException("User ID in path must match user ID in request");
        }

        UserContactResponse response = userContactService.createUserContact(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(response, "User contact created successfully"));
    }

    /**
     * Gets a user's contact by user ID.
     */
    @GetMapping("/{userId}")
    @Operation(summary = "Get user contact", description = "Gets the contact information for the specified user")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<ApiResponse<UserContactResponse>> getUserContact(
        @PathVariable @Parameter(description = "User ID") UUID userId
    ) {
        log.info("Fetching contact for user: {}", userId);
        UserContactResponse response = userContactService.getUserContact(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Gets a user's contact by contact ID.
     */
    @GetMapping("/contact/{contactId}")
    @Operation(summary = "Get user contact by ID", description = "Gets the contact information by contact ID")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<ApiResponse<UserContactResponse>> getUserContactById(
        @PathVariable @Parameter(description = "Contact ID") UUID contactId
    ) {
        log.info("Fetching user contact by ID: {}", contactId);
        UserContactResponse response = userContactService.getUserContactById(contactId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Updates a user's contact.
     */
    @PutMapping("/{userId}")
    @Operation(summary = "Update user contact", description = "Updates the contact information for the specified user")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<ApiResponse<UserContactResponse>> updateUserContact(
        @PathVariable @Parameter(description = "User ID") UUID userId,
        @Valid @RequestBody UpdateUserContactRequest request
    ) {
        log.info("Updating contact for user: {}", userId);
        UserContactResponse response = userContactService.updateUserContact(userId, request);
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
    public ResponseEntity<ApiResponse<PageResponse<UserContactListResponse>>> getUserContacts(
        @Parameter(description = "Tenant ID") @RequestParam(required = false) UUID tenantId,
        @Parameter(description = "Page number") @RequestParam(defaultValue = "0") @Min(0) int page,
        @Parameter(description = "Page size") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
        @Parameter(description = "Sort by") @RequestParam(defaultValue = "userDisplayName") String sortBy,
        @Parameter(description = "Sort direction") @RequestParam(defaultValue = "ASC") String sortDirection
    ) {
        log.info("Fetching user contacts - page: {}, size: {}", page, size);

        PageRequestDto pageRequest = PageRequestDto.builder()
            .page(page)
            .size(size)
            .sortBy(sortBy)
            .sortDirection(sortDirection)
            .build();

        PageResponse<UserContactListResponse> response = userContactService.getUserContactsByTenant(
            tenantId != null ? tenantId : getCurrentTenantId(),
            pageRequest
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Searches user contacts by name or contact information.
     */
    @GetMapping("/search")
    @Operation(summary = "Search user contacts", description = "Searches user contacts by name or contact information")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<UserContactListResponse>>> searchUserContacts(
        @Parameter(description = "Search query") @RequestParam String query,
        @Parameter(description = "Page number") @RequestParam(defaultValue = "0") @Min(0) int page,
        @Parameter(description = "Page size") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        log.info("Searching user contacts with query: {}", query);

        PageRequestDto pageRequest = PageRequestDto.builder().page(page).size(size).build();
        PageResponse<UserContactListResponse> response = userContactService.searchUserContacts(query, pageRequest);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Gets user contacts by preferred contact method.
     */
    @GetMapping("/by-contact-method/{method}")
    @Operation(summary = "Get user contacts by preferred method", description = "Gets user contacts by their preferred contact method")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<UserContactResponse>>> getUserContactsByPreferredMethod(
        @PathVariable @Parameter(description = "Contact method (EMAIL, PHONE, SMS)") String method
    ) {
        log.info("Fetching user contacts with preferred method: {}", method);
        List<UserContactResponse> response = userContactService.getUserContactsByPreferredMethod(method);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Gets public user profiles.
     */
    @GetMapping("/public")
    @Operation(summary = "Get public user profiles", description = "Gets user contacts with public profiles")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<ApiResponse<List<UserContactListResponse>>> getPublicUserProfiles() {
        log.info("Fetching public user profiles");
        List<UserContactListResponse> response = userContactService.getPublicUserProfiles();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Gets user contacts that allow direct messages.
     */
    @GetMapping("/allow-messages")
    @Operation(summary = "Get users allowing direct messages", description = "Gets user contacts that allow direct messages")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<ApiResponse<List<UserContactListResponse>>> getUserContactsAllowingDirectMessages() {
        log.info("Fetching user contacts allowing direct messages");
        List<UserContactListResponse> response = userContactService.getUserContactsAllowingDirectMessages();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Updates user contact privacy settings.
     */
    @PutMapping("/{userId}/privacy")
    @Operation(summary = "Update privacy settings", description = "Updates user contact privacy settings")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<ApiResponse<UserContactResponse>> updatePrivacySettings(
        @PathVariable @Parameter(description = "User ID") UUID userId,
        @Parameter(description = "Public profile") @RequestParam boolean publicProfile,
        @Parameter(description = "Allow direct messages") @RequestParam boolean allowDirectMessages,
        @Parameter(description = "Allow notifications") @RequestParam boolean allowNotifications
    ) {
        log.info("Updating privacy settings for user: {}", userId);
        UserContactResponse response = userContactService.updatePrivacySettings(
            userId, publicProfile, allowDirectMessages, allowNotifications);
        return ResponseEntity.ok(ApiResponse.success(response, "Privacy settings updated successfully"));
    }

    /**
     * Updates user contact emergency contact information.
     */
    @PutMapping("/{userId}/emergency-contact")
    @Operation(summary = "Update emergency contact", description = "Updates user emergency contact information")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<ApiResponse<UserContactResponse>> updateEmergencyContact(
        @PathVariable @Parameter(description = "User ID") UUID userId,
        @Parameter(description = "Emergency contact name") @RequestParam String name,
        @Parameter(description = "Emergency contact phone") @RequestParam String phone,
        @Parameter(description = "Emergency contact relation") @RequestParam String relation
    ) {
        log.info("Updating emergency contact for user: {}", userId);
        UserContactResponse response = userContactService.updateEmergencyContact(userId, name, phone, relation);
        return ResponseEntity.ok(ApiResponse.success(response, "Emergency contact updated successfully"));
    }

    /**
     * Gets the current tenant ID from security context.
     */
    private UUID getCurrentTenantId() {
        return SecurityContextUtil.getCurrentTenantId();
    }
}