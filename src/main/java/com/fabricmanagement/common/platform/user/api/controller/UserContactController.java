package com.fabricmanagement.common.platform.user.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.platform.communication.app.ContactService;
import com.fabricmanagement.common.platform.communication.domain.Contact;
import com.fabricmanagement.common.platform.communication.dto.AssignContactRequest;
import com.fabricmanagement.common.platform.communication.dto.CreateContactRequest;
import com.fabricmanagement.common.platform.user.app.UserContactAssignmentService;
import com.fabricmanagement.common.platform.user.domain.UserContact;
import com.fabricmanagement.common.platform.user.dto.UserContactDto;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/common/users/{userId}/contacts")
@RequiredArgsConstructor
@Slf4j
public class UserContactController {

  private final UserContactAssignmentService userContactAssignmentService;
  private final ContactService contactService;

  @GetMapping
  public ResponseEntity<ApiResponse<List<UserContactDto>>> getUserContacts(
      @PathVariable UUID userId) {
    log.debug("Getting user contacts: userId={}", userId);

    List<UserContactDto> contacts =
        userContactAssignmentService.getUserContacts(userId).stream()
            .map(UserContactDto::from)
            .collect(Collectors.toList());

    return ResponseEntity.ok(ApiResponse.success(contacts));
  }

  @GetMapping("/default")
  public ResponseEntity<ApiResponse<UserContactDto>> getDefaultContact(@PathVariable UUID userId) {
    log.debug("Getting default contact: userId={}", userId);

    UserContact defaultContact =
        userContactAssignmentService
            .getDefaultContact(userId)
            .orElseThrow(() -> new IllegalArgumentException("No default contact found"));

    return ResponseEntity.ok(ApiResponse.success(UserContactDto.from(defaultContact)));
  }

  @GetMapping("/authentication")
  public ResponseEntity<ApiResponse<UserContactDto>> getAuthenticationContact(
      @PathVariable UUID userId) {
    log.debug("Getting verified contact for authentication: userId={}", userId);

    UserContact authContact =
        userContactAssignmentService
            .getAuthenticationContact(userId)
            .orElseThrow(() -> new IllegalArgumentException("No verified contact found"));

    return ResponseEntity.ok(ApiResponse.success(UserContactDto.from(authContact)));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<UserContactDto>> assignContact(
      @PathVariable UUID userId, @Valid @RequestBody AssignContactRequest request) {
    log.info("Assigning contact to user: userId={}, request={}", userId, request);

    UserContact userContact =
        userContactAssignmentService.assignContact(
            userId, request.getContactId(), request.getIsDefault());

    return ResponseEntity.ok(
        ApiResponse.success(UserContactDto.from(userContact), "Contact assigned successfully"));
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

    Contact contact =
        contactService.createContact(
            createRequest.getContactValue(),
            createRequest.getContactType(),
            createRequest.getLabel(),
            createRequest.getIsPersonal(),
            createRequest.getParentContactId());

    UserContact userContact =
        userContactAssignmentService.assignContact(userId, contact.getId(), isDefault);

    return ResponseEntity.ok(
        ApiResponse.success(
            UserContactDto.from(userContact), "Contact created and assigned successfully"));
  }

  @PutMapping("/{contactId}/default")
  public ResponseEntity<ApiResponse<UserContactDto>> setAsDefault(
      @PathVariable UUID userId, @PathVariable UUID contactId) {
    log.info("Setting default contact: userId={}, contactId={}", userId, contactId);

    UserContact userContact = userContactAssignmentService.setAsDefault(userId, contactId);

    return ResponseEntity.ok(
        ApiResponse.success(UserContactDto.from(userContact), "Default contact set successfully"));
  }

  @DeleteMapping("/{contactId}")
  public ResponseEntity<ApiResponse<Void>> removeContact(
      @PathVariable UUID userId, @PathVariable UUID contactId) {
    log.info("Removing contact from user: userId={}, contactId={}", userId, contactId);

    userContactAssignmentService.removeContact(userId, contactId);

    return ResponseEntity.ok(ApiResponse.success(null, "Contact removed successfully"));
  }
}
