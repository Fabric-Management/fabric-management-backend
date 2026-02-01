package com.fabricmanagement.common.platform.company.app;

import com.fabricmanagement.common.infrastructure.assignment.BaseAssignmentService;
import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.communication.domain.Contact;
import com.fabricmanagement.common.platform.communication.infra.repository.ContactRepository;
import com.fabricmanagement.common.platform.company.domain.CompanyContact;
import com.fabricmanagement.common.platform.company.domain.event.ContactAssignedEvent;
import com.fabricmanagement.common.platform.company.infra.repository.CompanyContactRepository;
import com.fabricmanagement.common.platform.company.infra.repository.CompanyRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Company–Contact assignment service. Extends {@link BaseAssignmentService}; adds department and
 * assign overload.
 */
@Service
@Slf4j
public class CompanyContactAssignmentService extends BaseAssignmentService<CompanyContact> {

  private final CompanyRepository companyRepository;
  private final ContactRepository contactRepository;
  private final CompanyContactRepository companyContactRepository;
  private final DomainEventPublisher eventPublisher;

  public CompanyContactAssignmentService(
      CompanyRepository companyRepository,
      ContactRepository contactRepository,
      CompanyContactRepository companyContactRepository,
      DomainEventPublisher eventPublisher) {
    this.companyRepository = companyRepository;
    this.contactRepository = contactRepository;
    this.companyContactRepository = companyContactRepository;
    this.eventPublisher = eventPublisher;
  }

  @Override
  protected void onAfterAssign(CompanyContact junction) {
    eventPublisher.publish(
        new ContactAssignedEvent(
            TenantContext.getCurrentTenantId(), junction.getCompanyId(), junction.getContactId()));
  }

  @Override
  protected JpaRepository<CompanyContact, ?> getRepository() {
    return companyContactRepository;
  }

  @Override
  protected void validateParentExists(UUID parentId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    companyRepository
        .findByTenantIdAndId(tenantId, parentId)
        .orElseThrow(() -> new IllegalArgumentException("Company not found"));
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
  protected Optional<CompanyContact> findExisting(UUID parentId, UUID childId) {
    return companyContactRepository.findByCompanyIdAndContactId(parentId, childId);
  }

  @Override
  protected Optional<CompanyContact> findPrimaryByParent(UUID parentId) {
    return companyContactRepository.findDefaultByCompanyId(parentId);
  }

  @Override
  protected List<CompanyContact> findByParent(UUID parentId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return companyContactRepository.findByTenantIdAndCompanyId(tenantId, parentId);
  }

  @Override
  protected CompanyContact buildJunction(UUID parentId, UUID childId, Boolean primaryFlag) {
    return CompanyContact.builder()
        .companyId(parentId)
        .contactId(childId)
        .isDefault(Boolean.TRUE.equals(primaryFlag))
        .department(null)
        .build();
  }

  /** Assign contact to company with optional department. */
  @Transactional
  public CompanyContact assignContact(
      UUID companyId, UUID contactId, Boolean isDefault, String department) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.info(
        "Assigning contact to company: companyId={}, contactId={}, isDefault={}, department={}",
        companyId,
        contactId,
        isDefault,
        department);

    validateParentExists(companyId);
    validateChildExists(contactId);
    validateAssignment(companyId, contactId);

    if (companyContactRepository.findByCompanyIdAndContactId(companyId, contactId).isPresent()) {
      throw new IllegalArgumentException("Contact is already assigned to this company");
    }

    if (Boolean.TRUE.equals(isDefault)) {
      companyContactRepository
          .findDefaultByCompanyId(companyId)
          .ifPresent(
              existing -> {
                existing.setIsDefault(false);
                companyContactRepository.save(existing);
              });
    }

    CompanyContact junction =
        CompanyContact.builder()
            .companyId(companyId)
            .contactId(contactId)
            .isDefault(isDefault != null ? isDefault : false)
            .department(department)
            .build();
    CompanyContact saved = companyContactRepository.save(junction);
    onAfterAssign(saved);
    return saved;
  }

  @Transactional
  public void removeContact(UUID companyId, UUID contactId) {
    unassign(companyId, contactId);
  }

  @Transactional
  public CompanyContact setAsDefault(UUID companyId, UUID contactId) {
    return setPrimary(companyId, contactId);
  }

  @Transactional(readOnly = true)
  public List<CompanyContact> getCompanyContacts(UUID companyId) {
    return getByParent(companyId);
  }

  @Transactional(readOnly = true)
  public Optional<CompanyContact> getDefaultContact(UUID companyId) {
    return getPrimary(companyId);
  }

  @Transactional(readOnly = true)
  public List<CompanyContact> getDepartmentContacts(UUID companyId, String department) {
    return companyContactRepository.findByCompanyIdAndDepartment(companyId, department);
  }
}
