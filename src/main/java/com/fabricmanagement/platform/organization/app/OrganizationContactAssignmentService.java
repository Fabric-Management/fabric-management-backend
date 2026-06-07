package com.fabricmanagement.platform.organization.app;

import com.fabricmanagement.common.infrastructure.assignment.BaseAssignmentService;
import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.CommonDomainException;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.communication.app.ContactService;
import com.fabricmanagement.platform.communication.domain.Contact;
import com.fabricmanagement.platform.communication.domain.ContactType;
import com.fabricmanagement.platform.communication.infra.repository.ContactRepository;
import com.fabricmanagement.platform.organization.domain.OrganizationContact;
import com.fabricmanagement.platform.organization.domain.event.OrganizationContactAssignedEvent;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationContactRepository;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Organization–Contact assignment service. Extends {@link BaseAssignmentService}; adds department
 * and assign overload.
 */
@Service
@Slf4j
public class OrganizationContactAssignmentService
    extends BaseAssignmentService<OrganizationContact> {

  private final OrganizationRepository organizationRepository;
  private final ContactRepository contactRepository;
  private final OrganizationContactRepository organizationContactRepository;
  private final DomainEventPublisher eventPublisher;
  private final ContactService contactService;

  public OrganizationContactAssignmentService(
      OrganizationRepository organizationRepository,
      ContactRepository contactRepository,
      OrganizationContactRepository organizationContactRepository,
      DomainEventPublisher eventPublisher,
      ContactService contactService) {
    this.organizationRepository = organizationRepository;
    this.contactRepository = contactRepository;
    this.organizationContactRepository = organizationContactRepository;
    this.eventPublisher = eventPublisher;
    this.contactService = contactService;
  }

  @Override
  protected void onAfterAssign(OrganizationContact junction) {
    eventPublisher.publish(
        new OrganizationContactAssignedEvent(
            TenantContext.requireTenantId(),
            junction.getOrganizationId(),
            junction.getContactId()));
  }

  @Override
  protected JpaRepository<OrganizationContact, ?> getRepository() {
    return organizationContactRepository;
  }

  @Override
  protected void validateParentExists(UUID parentId) {
    UUID tenantId = TenantContext.requireTenantId();
    organizationRepository
        .findByTenantIdAndId(tenantId, parentId)
        .orElseThrow(
            () -> new PlatformDomainException("Organization not found", "ORG_NOT_FOUND", 404));
  }

  @Override
  protected void validateChildExists(UUID childId) {
    UUID tenantId = TenantContext.requireTenantId();
    Contact contact =
        contactRepository
            .findById(childId)
            .orElseThrow(
                () ->
                    new PlatformDomainException("Contact not found", "ORG_CONTACT_NOT_FOUND", 404));
    if (!contact.getTenantId().equals(tenantId)) {
      throw new PlatformDomainException(
          "Contact does not belong to current tenant", "ORG_CONTACT_TENANT_MISMATCH", 403);
    }
  }

  @Override
  protected Optional<OrganizationContact> findExisting(UUID parentId, UUID childId) {
    return organizationContactRepository.findByOrganizationIdAndContactId(parentId, childId);
  }

  @Override
  protected Optional<OrganizationContact> findPrimaryByParent(UUID parentId) {
    return organizationContactRepository.findDefaultByOrganizationId(parentId);
  }

  @Override
  protected List<OrganizationContact> findByParent(UUID parentId) {
    UUID tenantId = TenantContext.requireTenantId();
    return organizationContactRepository.findWithContactByTenantIdAndOrganizationId(
        tenantId, parentId);
  }

  @Override
  protected OrganizationContact buildJunction(UUID parentId, UUID childId, Boolean primaryFlag) {
    return OrganizationContact.builder()
        .organizationId(parentId)
        .contactId(childId)
        .isDefault(Boolean.TRUE.equals(primaryFlag))
        .department(null)
        .build();
  }

  /** Assign contact to organization with optional department. */
  @Transactional
  public OrganizationContact assignContact(
      UUID organizationId, UUID contactId, Boolean isDefault, String department) {
    log.info(
        "Assigning contact to organization: organizationId={}, contactId={}, isDefault={}, department={}",
        organizationId,
        contactId,
        isDefault,
        department);

    validateParentExists(organizationId);
    validateChildExists(contactId);
    validateAssignment(organizationId, contactId);

    if (organizationContactRepository
        .findByOrganizationIdAndContactId(organizationId, contactId)
        .isPresent()) {
      throw new PlatformDomainException(
          "Contact is already assigned to this organization", "ORG_CONTACT_ALREADY_ASSIGNED", 409);
    }

    if (Boolean.TRUE.equals(isDefault)) {
      organizationContactRepository
          .findDefaultByOrganizationId(organizationId)
          .ifPresent(
              existing -> {
                existing.setIsDefault(false);
                organizationContactRepository.save(existing);
              });
    }

    OrganizationContact junction =
        OrganizationContact.builder()
            .organizationId(organizationId)
            .contactId(contactId)
            .isDefault(isDefault != null ? isDefault : false)
            .department(department)
            .build();
    OrganizationContact saved = organizationContactRepository.save(junction);
    onAfterAssign(saved);
    return saved;
  }

  /**
   * Update mutable fields of an existing organization–contact junction.
   *
   * <p>Currently supports updating {@code department}. Pass {@code null} or empty string to clear
   * the department.
   */
  @Transactional
  public OrganizationContact updateContactAssignment(
      UUID organizationId, UUID contactId, String department) {
    UUID tenantId = TenantContext.requireTenantId();
    log.info(
        "Updating contact assignment: organizationId={}, contactId={}, department={}",
        organizationId,
        contactId,
        department);

    OrganizationContact oc =
        organizationContactRepository
            .findByOrganizationIdAndContactId(organizationId, contactId)
            .orElseThrow(
                () ->
                    new PlatformDomainException(
                        "Contact assignment not found", "ORG_CONTACT_ASSIGNMENT_NOT_FOUND", 404));

    if (!oc.getTenantId().equals(tenantId)) {
      throw new PlatformDomainException(
          "Contact assignment does not belong to current tenant",
          "ORG_CONTACT_ASSIGNMENT_MISMATCH",
          403);
    }

    oc.setDepartment(department == null || department.isBlank() ? null : department.trim());
    return organizationContactRepository.save(oc);
  }

  @Transactional
  public void removeContact(UUID organizationId, UUID contactId) {
    UUID tenantId = TenantContext.requireTenantId();

    OrganizationContact oc =
        organizationContactRepository
            .findByOrganizationIdAndContactId(organizationId, contactId)
            .orElseThrow(
                () ->
                    new PlatformDomainException(
                        "Contact assignment not found", "ORG_CONTACT_ASSIGNMENT_NOT_FOUND", 404));

    if (Boolean.TRUE.equals(oc.getIsDefault())) {
      throw new CommonDomainException(
          "Cannot remove the default contact. Please set another contact as default first.");
    }

    Contact contact =
        contactRepository
            .findById(contactId)
            .filter(c -> c.getTenantId().equals(tenantId))
            .orElse(null);

    if (contact != null && contact.getContactType() == ContactType.LANDLINE) {
      List<Contact> extensions =
          contactRepository.findExtensionsByParentContactId(tenantId, contactId);
      for (Contact ext : extensions) {
        organizationContactRepository
            .findByOrganizationIdAndContactId(organizationId, ext.getId())
            .ifPresent(
                extOc -> {
                  log.info(
                      "Cascade removing PHONE_EXTENSION assignment: orgId={}, extensionId={}",
                      organizationId,
                      ext.getId());
                  organizationContactRepository.delete(extOc);
                });
      }
    }

    unassign(organizationId, contactId);
  }

  @Transactional
  public OrganizationContact setAsDefault(UUID organizationId, UUID contactId) {
    return setPrimary(organizationId, contactId);
  }

  /**
   * Atomically edit both contact entity fields and organization–contact assignment fields in a
   * single transaction. Null values are ignored (patch semantics).
   */
  @Transactional
  public OrganizationContact editOrganizationContact(
      UUID organizationId,
      UUID contactId,
      String contactValue,
      ContactType contactType,
      String label,
      Boolean isPersonal,
      Boolean isDefault,
      String department) {
    UUID tenantId = TenantContext.requireTenantId();
    log.info(
        "Atomic edit: orgId={}, contactId={}, isDefault={}, department={}",
        organizationId,
        contactId,
        isDefault,
        department);

    OrganizationContact oc =
        organizationContactRepository
            .findByOrganizationIdAndContactId(organizationId, contactId)
            .orElseThrow(
                () ->
                    new PlatformDomainException(
                        "Contact assignment not found", "ORG_CONTACT_ASSIGNMENT_NOT_FOUND", 404));

    if (!oc.getTenantId().equals(tenantId)) {
      throw new CommonDomainException("Contact assignment does not belong to current tenant");
    }

    Contact contact =
        contactService.updateContactFields(contactId, contactValue, contactType, label, isPersonal);

    if (department != null) {
      oc.setDepartment(department.isBlank() ? null : department.trim());
    }

    if (Boolean.TRUE.equals(isDefault) && !Boolean.TRUE.equals(oc.getIsDefault())) {
      organizationContactRepository
          .findDefaultByOrganizationId(organizationId)
          .ifPresent(
              existing -> {
                existing.setIsDefault(false);
                organizationContactRepository.save(existing);
              });
      oc.setIsDefault(true);
    } else if (Boolean.FALSE.equals(isDefault) && Boolean.TRUE.equals(oc.getIsDefault())) {
      throw new CommonDomainException(
          "Cannot unset the default contact without assigning another contact as default first.");
    }

    OrganizationContact saved = organizationContactRepository.save(oc);
    saved.setContact(contact);
    return saved;
  }

  @Transactional(readOnly = true)
  public List<OrganizationContact> getOrganizationContacts(UUID organizationId) {
    return getByParent(organizationId);
  }

  @Transactional(readOnly = true)
  public Optional<OrganizationContact> getDefaultContact(UUID organizationId) {
    return getPrimary(organizationId);
  }
}
