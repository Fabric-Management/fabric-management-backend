package com.fabricmanagement.platform.organization.api.facade;

import com.fabricmanagement.platform.communication.app.ContactService;
import com.fabricmanagement.platform.communication.domain.Contact;
import com.fabricmanagement.platform.communication.dto.AssignContactRequest;
import com.fabricmanagement.platform.communication.dto.CreateContactRequest;
import com.fabricmanagement.platform.organization.app.OrganizationContactAssignmentService;
import com.fabricmanagement.platform.organization.domain.OrganizationContact;
import com.fabricmanagement.platform.organization.dto.EditOrganizationContactRequest;
import com.fabricmanagement.platform.organization.dto.OrganizationContactDto;
import com.fabricmanagement.platform.organization.dto.UpdateContactAssignmentRequest;
import com.fabricmanagement.platform.organization.mapper.OrganizationContactMapper;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrganizationContactFacade {

  private final OrganizationContactAssignmentService assignmentService;
  private final ContactService contactService;
  private final OrganizationContactMapper mapper;

  @Transactional(readOnly = true)
  public List<OrganizationContactDto> getOrganizationContacts(UUID organizationId) {
    return assignmentService.getOrganizationContacts(organizationId).stream()
        .map(mapper::toDto)
        .collect(Collectors.toList());
  }

  @Transactional(readOnly = true)
  public OrganizationContactDto getDefaultContact(UUID organizationId) {
    OrganizationContact contact =
        assignmentService
            .getDefaultContact(organizationId)
            .orElseThrow(() -> new IllegalArgumentException("No default contact found"));
    return mapper.toDto(contact);
  }

  @Transactional
  public OrganizationContactDto assignContact(UUID organizationId, AssignContactRequest request) {
    OrganizationContact contact =
        assignmentService.assignContact(
            organizationId,
            request.getContactId(),
            request.getIsDefault(),
            request.getDepartment());
    return mapper.toDto(contact);
  }

  @Transactional
  public OrganizationContactDto createAndAssignContact(
      UUID organizationId, CreateContactRequest request, Boolean isDefault, String department) {
    Contact contact =
        contactService.createContact(
            request.getContactValue(),
            request.getContactType(),
            request.getLabel(),
            request.getIsPersonal(),
            request.getParentContactId());
    OrganizationContact orgContact =
        assignmentService.assignContact(organizationId, contact.getId(), isDefault, department);
    return mapper.toDto(orgContact);
  }

  @Transactional
  public OrganizationContactDto updateContactAssignment(
      UUID organizationId, UUID contactId, UpdateContactAssignmentRequest request) {
    return mapper.toDto(
        assignmentService.updateContactAssignment(
            organizationId, contactId, request.getDepartment()));
  }

  @Transactional
  public OrganizationContactDto setAsDefault(UUID organizationId, UUID contactId) {
    return mapper.toDto(assignmentService.setAsDefault(organizationId, contactId));
  }

  @Transactional
  public OrganizationContactDto editOrganizationContact(
      UUID organizationId, UUID contactId, EditOrganizationContactRequest request) {
    return mapper.toDto(
        assignmentService.editOrganizationContact(
            organizationId,
            contactId,
            request.getContactValue(),
            request.getContactType(),
            request.getLabel(),
            request.getIsPersonal(),
            request.getIsDefault(),
            request.getDepartment()));
  }

  @Transactional
  public void removeContact(UUID organizationId, UUID contactId) {
    assignmentService.removeContact(organizationId, contactId);
  }
}
