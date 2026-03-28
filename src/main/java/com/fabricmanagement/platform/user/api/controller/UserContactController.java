package com.fabricmanagement.platform.user.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.platform.communication.dto.AssignContactRequest;
import com.fabricmanagement.platform.communication.dto.CreateContactRequest;
import com.fabricmanagement.platform.user.api.facade.UserContactFacade;
import com.fabricmanagement.platform.user.dto.UserContactDto;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/common/users/{userId}/contacts")
@RequiredArgsConstructor
@Slf4j
public class UserContactController {

  private final UserContactFacade userContactFacade;

  @GetMapping
  public ResponseEntity<ApiResponse<List<UserContactDto>>> getUserContacts(
      @PathVariable UUID userId) {
    log.debug("Getting user contacts: userId={}", userId);

    List<UserContactDto> contacts = userContactFacade.getUserContacts(userId);

    return ResponseEntity.ok(ApiResponse.success(contacts));
  }

  @GetMapping("/default")
  public ResponseEntity<ApiResponse<UserContactDto>> getDefaultContact(@PathVariable UUID userId) {
    log.debug("Getting default contact: userId={}", userId);

    UserContactDto defaultContact =
        userContactFacade
            .getDefaultContact(userId)
            .orElseThrow(() -> new IllegalArgumentException("No default contact found"));

    return ResponseEntity.ok(ApiResponse.success(defaultContact));
  }

  @GetMapping("/authentication")
  public ResponseEntity<ApiResponse<UserContactDto>> getAuthenticationContact(
      @PathVariable UUID userId) {
    log.debug("Getting verified contact for authentication: userId={}", userId);

    UserContactDto authContact =
        userContactFacade
            .getAuthenticationContact(userId)
            .orElseThrow(() -> new IllegalArgumentException("No verified contact found"));

    return ResponseEntity.ok(ApiResponse.success(authContact));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<UserContactDto>> assignContact(
      @PathVariable UUID userId, @Valid @RequestBody AssignContactRequest request) {
    log.info("Assigning contact to user: userId={}, request={}", userId, request);

    UserContactDto userContact =
        userContactFacade.assignContact(userId, request.getContactId(), request.getIsDefault());

    return ResponseEntity.ok(ApiResponse.success(userContact, "Contact assigned successfully"));
  }

  @PostMapping("/create-and-assign")
  public ResponseEntity<ApiResponse<UserContactDto>> createAndAssignContact(
      @PathVariable UUID userId,
      @Valid @RequestBody CreateContactRequest createRequest,
      @RequestParam(defaultValue = "false") Boolean isDefault) {
    log.info(
        "Creating and assigning contact to user: userId={}, type={}",
        userId,
        createRequest.getContactType());

    UserContactDto userContact =
        userContactFacade.createAndAssignContact(userId, createRequest, isDefault);

    return ResponseEntity.ok(
        ApiResponse.success(userContact, "Contact created and assigned successfully"));
  }

  @PutMapping("/{contactId}/default")
  public ResponseEntity<ApiResponse<UserContactDto>> setAsDefault(
      @PathVariable UUID userId, @PathVariable UUID contactId) {
    log.info("Setting default contact: userId={}, contactId={}", userId, contactId);

    UserContactDto userContact = userContactFacade.setAsDefault(userId, contactId);

    return ResponseEntity.ok(ApiResponse.success(userContact, "Default contact set successfully"));
  }

  @DeleteMapping("/{contactId}")
  public ResponseEntity<ApiResponse<Void>> removeContact(
      @PathVariable UUID userId, @PathVariable UUID contactId) {
    log.info("Removing contact from user: userId={}, contactId={}", userId, contactId);

    userContactFacade.removeContact(userId, contactId);

    return ResponseEntity.ok(ApiResponse.success(null, "Contact removed successfully"));
  }
}
