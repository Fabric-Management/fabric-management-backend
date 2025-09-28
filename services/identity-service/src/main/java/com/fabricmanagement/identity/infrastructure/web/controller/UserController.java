package com.fabricmanagement.identity.infrastructure.web.controller;

import com.fabricmanagement.identity.application.dto.user.*;
import com.fabricmanagement.identity.application.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST Controller for user profile and contact management.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User Management", description = "User profile and contact management endpoints")
public class UserController {

    private final UserService userService;

    @GetMapping("/profile")
    @Operation(summary = "Get user profile", description = "Returns current user's profile information")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Profile retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<UserProfileResponse> getProfile(Authentication authentication) {
        log.info("Get profile request");
        
        UserProfileResponse response = userService.getCurrentUserProfile(authentication);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/profile")
    @Operation(summary = "Update user profile", description = "Updates current user's profile information")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Profile updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid input data"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<UserProfileResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request, 
            Authentication authentication) {
        log.info("Update profile request");
        
        UserProfileResponse response = userService.updateCurrentUserProfile(request, authentication);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/contacts")
    @Operation(summary = "Get user contacts", description = "Returns all contacts for current user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Contacts retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<List<UserContactResponse>> getContacts(Authentication authentication) {
        log.info("Get contacts request");
        
        List<UserContactResponse> response = userService.getUserContacts(authentication);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/contacts")
    @Operation(summary = "Add user contact", description = "Adds a new contact (email/phone) to current user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Contact added successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid input data"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "409", description = "Contact already exists")
    })
    public ResponseEntity<UserContactResponse> addContact(
            @Valid @RequestBody AddContactRequest request, 
            Authentication authentication) {
        log.info("Add contact request: {}", request.getContactType());
        
        UserContactResponse response = userService.addUserContact(request, authentication);
        return ResponseEntity.status(201).body(response);
    }

    @DeleteMapping("/contacts/{contactId}")
    @Operation(summary = "Remove user contact", description = "Removes a contact from current user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Contact removed successfully"),
        @ApiResponse(responseCode = "400", description = "Cannot remove primary contact"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Contact not found")
    })
    public ResponseEntity<Map<String, String>> removeContact(
            @PathVariable String contactId, 
            Authentication authentication) {
        log.info("Remove contact request: {}", contactId);
        
        userService.removeUserContact(contactId, authentication);
        return ResponseEntity.ok(Map.of("message", "Contact removed successfully"));
    }

    @PutMapping("/contacts/{contactId}/primary")
    @Operation(summary = "Set primary contact", description = "Sets a contact as primary for current user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Primary contact set successfully"),
        @ApiResponse(responseCode = "400", description = "Contact not verified"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Contact not found")
    })
    public ResponseEntity<Map<String, String>> setPrimaryContact(
            @PathVariable String contactId, 
            Authentication authentication) {
        log.info("Set primary contact request: {}", contactId);
        
        userService.setPrimaryContact(contactId, authentication);
        return ResponseEntity.ok(Map.of("message", "Primary contact set successfully"));
    }

    @PostMapping("/2fa/enable")
    @Operation(summary = "Enable two-factor authentication", description = "Enables 2FA for current user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "2FA enabled successfully"),
        @ApiResponse(responseCode = "400", description = "2FA already enabled"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<Map<String, String>> enableTwoFactor(Authentication authentication) {
        log.info("Enable 2FA request");
        
        String secret = userService.enableTwoFactor(authentication);
        return ResponseEntity.ok(Map.of(
            "message", "Two-factor authentication enabled",
            "secret", secret
        ));
    }

    @PostMapping("/2fa/disable")
    @Operation(summary = "Disable two-factor authentication", description = "Disables 2FA for current user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "2FA disabled successfully"),
        @ApiResponse(responseCode = "400", description = "2FA not enabled"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<Map<String, String>> disableTwoFactor(Authentication authentication) {
        log.info("Disable 2FA request");
        
        userService.disableTwoFactor(authentication);
        return ResponseEntity.ok(Map.of("message", "Two-factor authentication disabled"));
    }
}
