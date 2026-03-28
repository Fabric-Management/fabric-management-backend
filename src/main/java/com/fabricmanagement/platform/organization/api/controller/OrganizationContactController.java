package com.fabricmanagement.platform.organization.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.platform.communication.dto.AssignContactRequest;
import com.fabricmanagement.platform.communication.dto.CreateContactRequest;
import com.fabricmanagement.platform.organization.api.facade.OrganizationContactFacade;
import com.fabricmanagement.platform.organization.dto.EditOrganizationContactRequest;
import com.fabricmanagement.platform.organization.dto.OrganizationContactDto;
import com.fabricmanagement.platform.organization.dto.UpdateContactAssignmentRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/common/organizations/{organizationId}/contacts")
@RequiredArgsConstructor
@Slf4j
public class OrganizationContactController {

  private final OrganizationContactFacade facade;

  @GetMapping
  public ResponseEntity<ApiResponse<List<OrganizationContactDto>>> getOrganizationContacts(
      @PathVariable UUID organizationId) {
    log.debug("Getting organization contacts: organizationId={}", organizationId);
    return ResponseEntity.ok(ApiResponse.success(facade.getOrganizationContacts(organizationId)));
  }

  @GetMapping("/default")
  public ResponseEntity<ApiResponse<OrganizationContactDto>> getDefaultContact(
      @PathVariable UUID organizationId) {
    log.debug("Getting default contact: organizationId={}", organizationId);
    return ResponseEntity.ok(ApiResponse.success(facade.getDefaultContact(organizationId)));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<OrganizationContactDto>> assignContact(
      @PathVariable UUID organizationId, @Valid @RequestBody AssignContactRequest request) {
    log.info(
        "Assigning contact to organization: organizationId={}, contactId={}, isDefault={}, department={}",
        organizationId,
        request.getContactId(),
        request.getIsDefault(),
        request.getDepartment());
    return ResponseEntity.ok(
        ApiResponse.success(
            facade.assignContact(organizationId, request), "Contact assigned successfully"));
  }

  @PostMapping("/create-and-assign")
  public ResponseEntity<ApiResponse<OrganizationContactDto>> createAndAssignContact(
      @PathVariable UUID organizationId,
      @Valid @RequestBody CreateContactRequest createRequest,
      @RequestParam(defaultValue = "false") Boolean isDefault,
      @RequestParam(required = false) String department) {
    log.info(
        "Creating and assigning contact to organization: organizationId={}, type={}, department={}",
        organizationId,
        createRequest.getContactType(),
        department);
    return ResponseEntity.ok(
        ApiResponse.success(
            facade.createAndAssignContact(organizationId, createRequest, isDefault, department),
            "Contact created and assigned successfully"));
  }

  @PatchMapping("/{contactId}")
  public ResponseEntity<ApiResponse<OrganizationContactDto>> updateContactAssignment(
      @PathVariable UUID organizationId,
      @PathVariable UUID contactId,
      @RequestBody UpdateContactAssignmentRequest request) {
    log.info(
        "Updating contact assignment: organizationId={}, contactId={}, department={}",
        organizationId,
        contactId,
        request.getDepartment());
    return ResponseEntity.ok(
        ApiResponse.success(
            facade.updateContactAssignment(organizationId, contactId, request),
            "Contact assignment updated successfully"));
  }

  @PutMapping("/{contactId}/default")
  public ResponseEntity<ApiResponse<OrganizationContactDto>> setAsDefault(
      @PathVariable UUID organizationId, @PathVariable UUID contactId) {
    log.info("Setting default contact: organizationId={}, contactId={}", organizationId, contactId);
    return ResponseEntity.ok(
        ApiResponse.success(
            facade.setAsDefault(organizationId, contactId), "Default contact set successfully"));
  }

  @PutMapping("/{contactId}/edit")
  public ResponseEntity<ApiResponse<OrganizationContactDto>> editOrganizationContact(
      @PathVariable UUID organizationId,
      @PathVariable UUID contactId,
      @RequestBody EditOrganizationContactRequest request) {
    log.info("Atomic edit contact: organizationId={}, contactId={}", organizationId, contactId);
    return ResponseEntity.ok(
        ApiResponse.success(
            facade.editOrganizationContact(organizationId, contactId, request),
            "Contact updated successfully"));
  }

  @DeleteMapping("/{contactId}")
  public ResponseEntity<ApiResponse<Void>> removeContact(
      @PathVariable UUID organizationId, @PathVariable UUID contactId) {
    log.info(
        "Removing contact from organization: organizationId={}, contactId={}",
        organizationId,
        contactId);
    facade.removeContact(organizationId, contactId);
    return ResponseEntity.ok(ApiResponse.success(null, "Contact removed successfully"));
  }
}
