package com.fabricmanagement.common.platform.organization.app;

import com.fabricmanagement.common.infrastructure.assignment.BaseAssignmentService;
import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.communication.domain.Contact;
import com.fabricmanagement.common.platform.communication.infra.repository.ContactRepository;
import com.fabricmanagement.common.platform.organization.domain.OrganizationContact;
import com.fabricmanagement.common.platform.organization.domain.event.OrganizationContactAssignedEvent;
import com.fabricmanagement.common.platform.organization.infra.repository.OrganizationContactRepository;
import com.fabricmanagement.common.platform.organization.infra.repository.OrganizationRepository;
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

  public OrganizationContactAssignmentService(
      OrganizationRepository organizationRepository,
      ContactRepository contactRepository,
      OrganizationContactRepository organizationContactRepository,
      DomainEventPublisher eventPublisher) {
    this.organizationRepository = organizationRepository;
    this.contactRepository = contactRepository;
    this.organizationContactRepository = organizationContactRepository;
    this.eventPublisher = eventPublisher;
  }

  @Override
  protected void onAfterAssign(OrganizationContact junction) {
    eventPublisher.publish(
        new OrganizationContactAssignedEvent(
            TenantContext.getCurrentTenantId(),
            junction.getOrganizationId(),
            junction.getContactId()));
  }

  @Override
  protected JpaRepository<OrganizationContact, ?> getRepository() {
    return organizationContactRepository;
  }

  @Override
  protected void validateParentExists(UUID parentId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    organizationRepository
        .findByTenantIdAndId(tenantId, parentId)
        .orElseThrow(() -> new IllegalArgumentException("Organization not found"));
  }

  @Override
  protected void validateChildExists(UUID childId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    Contact contact =
        contactRepository
            .findById(childId)
            .orElseThrow(() -> new IllegalArgumentException("Contact not found"));
    if (!contact.getTenantId().equals(tenantId)) {
      throw new IllegalArgumentException("Contact does not belong to current tenant");
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
    UUID tenantId = TenantContext.getCurrentTenantId();
    return organizationContactRepository.findWithContactByTenantIdAndOrganizationId(
        tenantId, parentId);
  }

  @Override
  protected OrganizationContact buildJunction(UUID parentId, UUID childId, Boolean primaryFlag) {
    return OrganizationContact.builder()
        .organizationId(parentId)
        .contactId(childId)
        .isDefault(Boolean.TRUE.equals(primaryFlag))
        .build();
  }

  /** Assign contact to organization with optional department. */
  @Transactional
  public OrganizationContact assignContact(UUID organizationId, UUID contactId, Boolean isDefault) {
    log.info(
        "Assigning contact to organization: organizationId={}, contactId={}, isDefault={}",
        organizationId,
        contactId,
        isDefault);

    validateParentExists(organizationId);
    validateChildExists(contactId);
    validateAssignment(organizationId, contactId);

    if (organizationContactRepository
        .findByOrganizationIdAndContactId(organizationId, contactId)
        .isPresent()) {
      throw new IllegalArgumentException("Contact is already assigned to this organization");
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
            .build();
    OrganizationContact saved = organizationContactRepository.save(junction);
    onAfterAssign(saved);
    return saved;
  }

  @Transactional
  public void removeContact(UUID organizationId, UUID contactId) {
    unassign(organizationId, contactId);
  }

  @Transactional
  public OrganizationContact setAsDefault(UUID organizationId, UUID contactId) {
    return setPrimary(organizationId, contactId);
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
