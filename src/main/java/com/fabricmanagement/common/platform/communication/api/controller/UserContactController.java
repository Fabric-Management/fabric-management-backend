package com.fabricmanagement.common.platform.communication.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.platform.communication.app.ContactService;
import com.fabricmanagement.common.platform.communication.app.UserContactService;
import com.fabricmanagement.common.platform.communication.domain.Contact;
import com.fabricmanagement.common.platform.communication.domain.UserContact;
import com.fabricmanagement.common.platform.communication.dto.AssignContactRequest;
import com.fabricmanagement.common.platform.communication.dto.CreateContactRequest;
import com.fabricmanagement.common.platform.communication.dto.UserContactDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/common/users/{userId}/contacts")
@RequiredArgsConstructor
@Slf4j
public class UserContactController {

    private final UserContactService userContactService;
    private final ContactService contactService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserContactDto>>> getUserContacts(@PathVariable UUID userId) {
        log.debug("Getting user contacts: userId={}", userId);

        List<UserContactDto> contacts = userContactService.getUserContacts(userId)
            .stream()
            .map(UserContactDto::from)
            .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(contacts));
    }

    @GetMapping("/default")
    public ResponseEntity<ApiResponse<UserContactDto>> getDefaultContact(@PathVariable UUID userId) {
        log.debug("Getting default contact: userId={}", userId);

        UserContact defaultContact = userContactService.getDefaultContact(userId)
            .orElseThrow(() -> new IllegalArgumentException("No default contact found"));

        return ResponseEntity.ok(ApiResponse.success(UserContactDto.from(defaultContact)));
    }

    @GetMapping("/authentication")
    public ResponseEntity<ApiResponse<UserContactDto>> getAuthenticationContact(@PathVariable UUID userId) {
        log.debug("Getting authentication contact: userId={}", userId);

        UserContact authContact = userContactService.getAuthenticationContact(userId)
            .orElseThrow(() -> new IllegalArgumentException("No authentication contact found"));

        return ResponseEntity.ok(ApiResponse.success(UserContactDto.from(authContact)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserContactDto>> assignContact(
            @PathVariable UUID userId,
            @Valid @RequestBody AssignContactRequest request) {
        log.info("Assigning contact to user: userId={}, contactId={}, isDefault={}, isForAuth={}",
            userId, request.getContactId(), request.getIsDefault(), request.getIsForAuthentication());

        UserContact userContact = userContactService.assignContact(
            userId,
            request.getContactId(),
            request.getIsDefault(),
            request.getIsForAuthentication()
        );

        return ResponseEntity.ok(ApiResponse.success(
            UserContactDto.from(userContact),
            "Contact assigned successfully"
        ));
    }

    @PostMapping("/create-and-assign")
    public ResponseEntity<ApiResponse<UserContactDto>> createAndAssignContact(
            @PathVariable UUID userId,
            @Valid @RequestBody CreateContactRequest createRequest,
            @RequestParam(defaultValue = "false") Boolean isDefault,
            @RequestParam(required = false) Boolean isForAuthentication) {
        log.info("Creating and assigning contact to user: userId={}, type={}", 
            userId, createRequest.getContactType());

        // Create contact first
        Contact contact = contactService.createContact(
            createRequest.getContactValue(),
            createRequest.getContactType(),
            createRequest.getLabel(),
            createRequest.getIsPersonal(),
            createRequest.getParentContactId()
        );

        // Assign to user
        UserContact userContact = userContactService.assignContact(
            userId,
            contact.getId(),
            isDefault,
            isForAuthentication
        );

        return ResponseEntity.ok(ApiResponse.success(
            UserContactDto.from(userContact),
            "Contact created and assigned successfully"
        ));
    }

    @PutMapping("/{contactId}/default")
    public ResponseEntity<ApiResponse<UserContactDto>> setAsDefault(
            @PathVariable UUID userId,
            @PathVariable UUID contactId) {
        log.info("Setting default contact: userId={}, contactId={}", userId, contactId);

        UserContact userContact = userContactService.setAsDefault(userId, contactId);

        return ResponseEntity.ok(ApiResponse.success(
            UserContactDto.from(userContact),
            "Default contact set successfully"
        ));
    }

    @PutMapping("/{contactId}/enable-auth")
    public ResponseEntity<ApiResponse<UserContactDto>> enableForAuthentication(
            @PathVariable UUID userId,
            @PathVariable UUID contactId) {
        log.info("Enabling contact for authentication: userId={}, contactId={}", userId, contactId);

        UserContact userContact = userContactService.enableForAuthentication(userId, contactId);

        return ResponseEntity.ok(ApiResponse.success(
            UserContactDto.from(userContact),
            "Contact enabled for authentication"
        ));
    }

    @DeleteMapping("/{contactId}")
    public ResponseEntity<ApiResponse<Void>> removeContact(
            @PathVariable UUID userId,
            @PathVariable UUID contactId) {
        log.info("Removing contact from user: userId={}, contactId={}", userId, contactId);

        userContactService.removeContact(userId, contactId);

        return ResponseEntity.ok(ApiResponse.success(null, "Contact removed successfully"));
    }
}

