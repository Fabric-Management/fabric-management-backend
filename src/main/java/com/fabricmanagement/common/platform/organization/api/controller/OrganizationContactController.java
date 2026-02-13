package com.fabricmanagement.common.platform.organization.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.platform.communication.app.ContactService;
import com.fabricmanagement.common.platform.communication.domain.Contact;
import com.fabricmanagement.common.platform.communication.dto.AssignContactRequest;
import com.fabricmanagement.common.platform.communication.dto.CreateContactRequest;
import com.fabricmanagement.common.platform.organization.app.OrganizationContactAssignmentService;
import com.fabricmanagement.common.platform.organization.domain.OrganizationContact;
import com.fabricmanagement.common.platform.organization.dto.OrganizationContactDto;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/common/organizations/{organizationId}/contacts")
@RequiredArgsConstructor
@Slf4j
public class OrganizationContactController {

  private final OrganizationContactAssignmentService organizationContactAssignmentService;
  private final ContactService contactService;

  @GetMapping
  public ResponseEntity<ApiResponse<List<OrganizationContactDto>>> getOrganizationContacts(
      @PathVariable UUID organizationId) {
    log.debug("Getting organization contacts: organizationId={}", organizationId);

    List<OrganizationContactDto> contacts =
        organizationContactAssignmentService.getOrganizationContacts(organizationId).stream()
            .map(OrganizationContactDto::from)
            .collect(Collectors.toList());

    return ResponseEntity.ok(ApiResponse.success(contacts));
  }

  @GetMapping("/default")
  public ResponseEntity<ApiResponse<OrganizationContactDto>> getDefaultContact(
      @PathVariable UUID organizationId) {
    log.debug("Getting default contact: organizationId={}", organizationId);

    OrganizationContact defaultContact =
        organizationContactAssignmentService
            .getDefaultContact(organizationId)
            .orElseThrow(() -> new IllegalArgumentException("No default contact found"));

    return ResponseEntity.ok(
        ApiResponse.success(OrganizationContactDto.from(defaultContact)));
  }

  @GetMapping("/department/{department}")
  public ResponseEntity<ApiResponse<List<OrganizationContactDto>>> getDepartmentContacts(
      @PathVariable UUID organizationId, @PathVariable String department) {
    log.debug(
        "Getting department contacts: organizationId={}, department={}",
        organizationId,
        department);

    List<OrganizationContactDto> contacts =
        organizationContactAssignmentService
            .getDepartmentContacts(organizationId, department)
            .stream()
            .map(OrganizationContactDto::from)
            .collect(Collectors.toList());

    return ResponseEntity.ok(ApiResponse.success(contacts));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<OrganizationContactDto>> assignContact(
      @PathVariable UUID organizationId, @Valid @RequestBody AssignContactRequest request) {
    log.info(
        "Assigning contact to organization: organizationId={}, contactId={}, isDefault={},"
            + " department={}",
        organizationId,
        request.getContactId(),
        request.getIsDefault(),
        request.getDepartment());

    OrganizationContact organizationContact =
        organizationContactAssignmentService.assignContact(
            organizationId,
            request.getContactId(),
            request.getIsDefault(),
            request.getDepartment());

    return ResponseEntity.ok(
        ApiResponse.success(
            OrganizationContactDto.from(organizationContact), "Contact assigned successfully"));
  }

  @PostMapping("/create-and-assign")
  public ResponseEntity<ApiResponse<OrganizationContactDto>> createAndAssignContact(
      @PathVariable UUID organizationId,
      @Valid @RequestBody CreateContactRequest createRequest,
      @RequestParam(defaultValue = "false") Boolean isDefault,
      @RequestParam(required = false) String department) {
    log.info(
        "Creating and assigning contact to organization: organizationId={}, type={}",
        organizationId,
        createRequest.getContactType());

    Contact contact =
        contactService.createContact(
            createRequest.getContactValue(),
            createRequest.getContactType(),
            createRequest.getLabel(),
            createRequest.getIsPersonal(),
            createRequest.getParentContactId());

    OrganizationContact organizationContact =
        organizationContactAssignmentService.assignContact(
            organizationId, contact.getId(), isDefault, department);

    return ResponseEntity.ok(
        ApiResponse.success(
            OrganizationContactDto.from(organizationContact),
            "Contact created and assigned successfully"));
  }

  @PutMapping("/{contactId}/default")
  public ResponseEntity<ApiResponse<OrganizationContactDto>> setAsDefault(
      @PathVariable UUID organizationId, @PathVariable UUID contactId) {
    log.info(
        "Setting default contact: organizationId={}, contactId={}", organizationId, contactId);

    OrganizationContact organizationContact =
        organizationContactAssignmentService.setAsDefault(organizationId, contactId);

    return ResponseEntity.ok(
        ApiResponse.success(
            OrganizationContactDto.from(organizationContact),
            "Default contact set successfully"));
  }

  @DeleteMapping("/{contactId}")
  public ResponseEntity<ApiResponse<Void>> removeContact(
      @PathVariable UUID organizationId, @PathVariable UUID contactId) {
    log.info(
        "Removing contact from organization: organizationId={}, contactId={}",
        organizationId,
        contactId);

    organizationContactAssignmentService.removeContact(organizationId, contactId);

    return ResponseEntity.ok(ApiResponse.success(null, "Contact removed successfully"));
  }
}
