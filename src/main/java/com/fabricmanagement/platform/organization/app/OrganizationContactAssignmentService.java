package com.fabricmanagement.platform.organization.app;

import com.fabricmanagement.common.infrastructure.assignment.BaseAssignmentService;
import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.CommonDomainException;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.communication.app.ContactDetailsOwnership;
import com.fabricmanagement.platform.communication.app.ContactService;
import com.fabricmanagement.platform.communication.domain.Contact;
import com.fabricmanagement.platform.communication.domain.ContactType;
import com.fabricmanagement.platform.communication.infra.repository.ContactRepository;
import com.fabricmanagement.platform.organization.domain.OrganizationContact;
import com.fabricmanagement.platform.organization.domain.event.OrganizationContactAssignedEvent;
import com.fabricmanagement.platform.organization.domain.event.OrganizationContactRemovedEvent;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationContactRepository;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.platform.organization.security.OrganizationWriteAccess;
import com.fabricmanagement.platform.user.infra.repository.UserContactRepository;
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
  private final OrganizationWriteAccess writeAccess;
  private final ContactDetailsOwnership ownership;
  private final UserContactRepository userContacts;
  private final ContactService contactService;

  public OrganizationContactAssignmentService(
      OrganizationRepository organizationRepository,
      ContactRepository contactRepository,
      OrganizationContactRepository organizationContactRepository,
      DomainEventPublisher eventPublisher,
      ContactService contactService,
      OrganizationWriteAccess writeAccess,
      ContactDetailsOwnership ownership,
      UserContactRepository userContacts) {
    this.organizationRepository = organizationRepository;
    this.contactRepository = contactRepository;
    this.organizationContactRepository = organizationContactRepository;
    this.eventPublisher = eventPublisher;
    this.writeAccess = writeAccess;
    this.ownership = ownership;
    this.userContacts = userContacts;
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
    lockContact(childId);
  }

  private Contact lockContact(UUID id) {
    return contactRepository
        .lockActiveByTenantAndId(TenantContext.requireTenantId(), id)
        .orElseThrow(
            () -> new PlatformDomainException("Contact not found", "ORG_CONTACT_NOT_FOUND", 404));
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

  /** Reuse the existing junction under the caller's root-organisation command lock. */
  @Transactional
  public OrganizationContact ensureOnboardingContact(
      UUID organizationId, UUID contactId, boolean isDefault) {
    validateParentExists(organizationId);
    validateChildExists(contactId);
    var existing = findExisting(organizationId, contactId);
    if (existing.isPresent()) {
      OrganizationContact assignment = existing.get();
      boolean reactivated = !Boolean.TRUE.equals(assignment.getIsActive());
      if (reactivated) {
        assignment.activate();
        assignment.setIsDefault(false);
      }
      if (isDefault) {
        organizationContactRepository
            .findByTenantIdAndOrganizationId(TenantContext.requireTenantId(), organizationId)
            .stream()
            .filter(other -> !other.getContactId().equals(contactId))
            .filter(other -> Boolean.TRUE.equals(other.getIsDefault()))
            .forEach(other -> other.setIsDefault(false));
        assignment.setIsDefault(true);
      }
      OrganizationContact saved = organizationContactRepository.save(assignment);
      if (reactivated) onAfterAssign(saved);
      return saved;
    }
    return attachContact(organizationId, contactId, isDefault, null, "Organisation", false);
  }

  /** Assign contact to organization with optional department. */
  @Transactional
  public OrganizationContact assignContact(
      UUID organizationId, UUID contactId, Boolean isDefault, String department) {
    validateParentExists(organizationId);
    validateChildExists(contactId);
    writeAccess.requireContactDetails(organizationId);
    ownership.requireContactReuse(contactId, false);
    ownership.requireExtensionParent(
        lockContact(contactId).getParentContactId(), organizationId, false);
    return attachContact(organizationId, contactId, isDefault, department, null, false);
  }

  @Transactional
  public OrganizationContact assignContact(
      UUID organizationId,
      UUID contactId,
      Boolean isDefault,
      String department,
      String label,
      Boolean isPersonal) {
    validateParentExists(organizationId);
    validateChildExists(contactId);
    writeAccess.requireContactDetails(organizationId);
    ownership.requireContactReuse(contactId, false);
    ownership.requireExtensionParent(
        lockContact(contactId).getParentContactId(), organizationId, false);
    return attachContact(organizationId, contactId, isDefault, department, label, isPersonal);
  }

  private OrganizationContact attachContact(
      UUID organizationId,
      UUID contactId,
      Boolean isDefault,
      String department,
      String label,
      Boolean isPersonal) {
    log.info(
        "Assigning contact to organization: organizationId={}, contactId={}, isDefault={},"
            + " department={}",
        organizationId,
        contactId,
        isDefault,
        department);

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
            .label(label)
            .isPersonal(Boolean.TRUE.equals(isPersonal))
            .contact(contactRepository.findById(contactId).orElseThrow())
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

    OrganizationContact oc = requireLink(organizationId, contactId);

    if (!oc.getTenantId().equals(tenantId)) {
      throw new PlatformDomainException(
          "Contact assignment does not belong to current tenant",
          "ORG_CONTACT_ASSIGNMENT_MISMATCH",
          403);
    }

    writeAccess.requireContactDetails(organizationId);
    oc.setDepartment(department == null || department.isBlank() ? null : department.trim());
    return organizationContactRepository.save(oc);
  }

  @Transactional
  public void removeContact(UUID organizationId, UUID contactId) {
    UUID tenantId = TenantContext.requireTenantId();

    Contact contact = lockContact(contactId);
    OrganizationContact oc = requireLink(organizationId, contactId);

    writeAccess.requireContactDetails(organizationId);
    if (Boolean.TRUE.equals(oc.getIsDefault())) {
      throw new CommonDomainException(
          "Cannot remove the default contact. Please set another contact as default first.");
    }

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
                  eventPublisher.publish(
                      new OrganizationContactRemovedEvent(tenantId, organizationId, ext.getId()));
                });
      }
    }

    organizationContactRepository.delete(oc);
    if (!hasOtherOwners(tenantId, contactId, organizationId)
        && (contact == null
            || contact.getContactType() != ContactType.LANDLINE
            || contactRepository.findExtensionsByParentContactId(tenantId, contactId).isEmpty())) {
      contactService.deleteContact(contactId);
    }
    // In this transaction: whatever rested on the contact for this organization ends with it.
    eventPublisher.publish(
        new OrganizationContactRemovedEvent(tenantId, organizationId, contactId));
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

    Contact contact = lockContact(contactId);
    OrganizationContact oc = requireLink(organizationId, contactId);

    if (!oc.getTenantId().equals(tenantId)) {
      throw new CommonDomainException("Contact assignment does not belong to current tenant");
    }

    writeAccess.requireContactDetails(organizationId);
    if (contactValue != null || contactType != null) {
      oc =
          changePoint(
              oc, contact, contactService.prepareChange(contact, contactValue, contactType));
      contact = oc.getContact();
    }
    if (label != null) oc.setLabel(label);
    if (isPersonal != null) oc.setIsPersonal(isPersonal);

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

  private OrganizationContact changePoint(
      OrganizationContact link, Contact old, ContactService.ContactPoint point) {
    if (contactService.samePoint(old, point)) return link;
    UUID tenant = TenantContext.requireTenantId();
    UUID organisationId = link.getOrganizationId();
    UUID oldId = old.getId();
    boolean shared = hasOtherOwners(tenant, oldId, organisationId);
    var destination =
        contactService
            .findByValueAndType(point.value(), point.type())
            .filter(contact -> !oldId.equals(contact.getId()));
    if (!shared && destination.isEmpty()) {
      if (old.getContactType() == ContactType.LANDLINE
          && !contactRepository.findExtensionsByParentContactId(tenant, oldId).isEmpty()) {
        throw new PlatformDomainException(
            "Remove the phone extensions before editing their landline",
            "ORG_CONTACT_HAS_EXTENSIONS",
            409);
      }
      link.setContact(contactService.replacePoint(old, point));
      return link;
    }
    if (old.getContactType() == ContactType.LANDLINE
        && !contactRepository.findExtensionsByParentContactId(tenant, oldId).isEmpty()) {
      throw new PlatformDomainException(
          "Remove the phone extensions before replacing their landline",
          "ORG_CONTACT_HAS_EXTENSIONS",
          409);
    }
    Contact next =
        destination
            .filter(contact -> Boolean.TRUE.equals(contact.getIsActive()))
            .orElseGet(
                () ->
                    contactService.createContact(
                        point.value(), point.type(), old.getParentContactId()));
    next = lockContact(next.getId());
    ownership.requireContactReuse(next.getId(), false);
    ownership.requireExtensionParent(next.getParentContactId(), organisationId, false);
    if (organizationContactRepository
        .findByOrganizationIdAndContactId(organisationId, next.getId())
        .isPresent()) {
      throw new PlatformDomainException(
          "Contact is already assigned to this organisation", "ORG_CONTACT_ALREADY_ASSIGNED", 409);
    }
    var replacement =
        OrganizationContact.builder()
            .organizationId(organisationId)
            .contactId(next.getId())
            .contact(next)
            .label(link.getLabel())
            .isPersonal(link.getIsPersonal())
            .isDefault(link.getIsDefault())
            .department(link.getDepartment())
            .build();
    organizationContactRepository.delete(link);
    eventPublisher.publish(new OrganizationContactRemovedEvent(tenant, organisationId, oldId));
    if (!shared) {
      contactService.deleteContact(oldId);
    }
    onAfterAssign(replacement);
    return replacement;
  }

  private boolean hasOtherOwners(UUID tenant, UUID contactId, UUID organisationId) {
    return organizationContactRepository
            .findByTenantIdAndContactIdAndIsActiveTrue(tenant, contactId)
            .stream()
            .anyMatch(link -> !organisationId.equals(link.getOrganizationId()))
        || userContacts.findByTenantIdAndContactId(tenant, contactId).stream()
            .anyMatch(link -> Boolean.TRUE.equals(link.getIsActive()));
  }

  @Override
  @Transactional
  public OrganizationContact assign(UUID organizationId, UUID contactId, Boolean isDefault) {
    return assignContact(organizationId, contactId, isDefault, null);
  }

  @Override
  @Transactional
  public OrganizationContact setPrimary(UUID organizationId, UUID contactId) {
    var link = requireLink(organizationId, contactId);
    writeAccess.requireContactDetails(organizationId);
    return super.setPrimary(link.getOrganizationId(), link.getContactId());
  }

  @Override
  @Transactional
  public void unassign(UUID organizationId, UUID contactId) {
    removeContact(organizationId, contactId);
  }

  private OrganizationContact requireLink(UUID organizationId, UUID contactId) {
    return findExisting(organizationId, contactId)
        .filter(link -> TenantContext.requireTenantId().equals(link.getTenantId()))
        .filter(link -> Boolean.TRUE.equals(link.getIsActive()))
        .orElseThrow(
            () ->
                new PlatformDomainException(
                    "Contact assignment not found", "ORG_CONTACT_ASSIGNMENT_NOT_FOUND", 404));
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
