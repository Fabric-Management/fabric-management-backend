package com.fabricmanagement.platform.user.app;

import com.fabricmanagement.common.infrastructure.assignment.BaseAssignmentService;
import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.communication.domain.Contact;
import com.fabricmanagement.platform.communication.infra.repository.ContactRepository;
import com.fabricmanagement.platform.user.domain.UserContact;
import com.fabricmanagement.platform.user.domain.event.ContactAssignedEvent;
import com.fabricmanagement.platform.user.infra.repository.UserContactRepository;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User–Contact assignment service. Extends {@link BaseAssignmentService}; adds
 * getAuthenticationContact, existsUserContact, removeContact (last-verified check),
 * getUserContactsByContactId.
 */
@Service
@Slf4j
public class UserContactAssignmentService extends BaseAssignmentService<UserContact> {

  private final UserRepository userRepository;
  private final ContactRepository contactRepository;
  private final UserContactRepository userContactRepository;
  private final DomainEventPublisher eventPublisher;

  public UserContactAssignmentService(
      UserRepository userRepository,
      ContactRepository contactRepository,
      UserContactRepository userContactRepository,
      DomainEventPublisher eventPublisher) {
    this.userRepository = userRepository;
    this.contactRepository = contactRepository;
    this.userContactRepository = userContactRepository;
    this.eventPublisher = eventPublisher;
  }

  @Override
  protected void onAfterAssign(UserContact junction) {
    eventPublisher.publish(
        new ContactAssignedEvent(
            TenantContext.getCurrentTenantId(), junction.getUserId(), junction.getContactId()));
  }

  @Override
  protected JpaRepository<UserContact, ?> getRepository() {
    return userContactRepository;
  }

  @Override
  protected void validateParentExists(UUID parentId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    userRepository
        .findByTenantIdAndId(tenantId, parentId)
        .orElseThrow(() -> new PlatformDomainException("User not found", "USER_NOT_FOUND", 404));
  }

  @Override
  protected void validateChildExists(UUID childId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    Contact contact =
        contactRepository
            .findById(childId)
            .orElseThrow(
                () ->
                    new PlatformDomainException(
                        "Contact not found", "USER_CONTACT_NOT_FOUND", 404));
    if (!contact.getTenantId().equals(tenantId)) {
      throw new PlatformDomainException(
          "Contact does not belong to current tenant", "USER_CONTACT_TENANT_MISMATCH", 403);
    }
  }

  @Override
  protected Optional<UserContact> findExisting(UUID parentId, UUID childId) {
    return userContactRepository.findByUserIdAndContactId(parentId, childId);
  }

  @Override
  protected Optional<UserContact> findPrimaryByParent(UUID parentId) {
    return userContactRepository.findDefaultByUserId(parentId);
  }

  @Override
  protected List<UserContact> findByParent(UUID parentId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return userContactRepository.findByTenantIdAndUserId(tenantId, parentId);
  }

  @Override
  protected UserContact buildJunction(UUID parentId, UUID childId, Boolean primaryFlag) {
    return UserContact.builder()
        .userId(parentId)
        .contactId(childId)
        .isDefault(Boolean.TRUE.equals(primaryFlag))
        .build();
  }

  @Transactional
  public UserContact assignContact(UUID userId, UUID contactId, Boolean isDefault) {
    return assign(userId, contactId, isDefault);
  }

  @Transactional
  public void removeContact(UUID userId, UUID contactId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    UserContact userContact =
        userContactRepository
            .findByUserIdAndContactId(userId, contactId)
            .orElseThrow(
                () ->
                    new PlatformDomainException(
                        "Contact assignment not found", "USER_CONTACT_ASSIGNMENT_NOT_FOUND", 404));

    Contact contact =
        contactRepository
            .findById(contactId)
            .orElseThrow(
                () ->
                    new PlatformDomainException(
                        "Contact not found", "USER_CONTACT_NOT_FOUND", 404));

    if (Boolean.TRUE.equals(contact.getIsVerified())) {
      long verifiedCount =
          userContactRepository.findByTenantIdAndUserId(tenantId, userId).stream()
              .map(uc -> contactRepository.findById(uc.getContactId()))
              .flatMap(Optional::stream)
              .filter(Contact::getIsVerified)
              .count();

      if (verifiedCount <= 1) {
        log.warn(
            "Attempt to remove last verified contact prevented: userId={}, contactId={}",
            userId,
            contactId);
        throw new PlatformDomainException(
            "Cannot remove the last verified contact. Add another verified contact first.",
            "USER_CONTACT_ASSIGNMENT_ERROR",
            400);
      }
    }

    userContactRepository.delete(userContact);
  }

  @Transactional
  public UserContact setAsDefault(UUID userId, UUID contactId) {
    return setPrimary(userId, contactId);
  }

  @Transactional(readOnly = true)
  public List<UserContact> getUserContacts(UUID userId) {
    return getByParent(userId);
  }

  @Transactional(readOnly = true)
  public Optional<UserContact> getDefaultContact(UUID userId) {
    return getPrimary(userId);
  }

  @Transactional(readOnly = true)
  public Optional<UserContact> getAuthenticationContact(UUID userId) {
    return userContactRepository.findPreferredContactByUserId(userId);
  }

  @Transactional(readOnly = true)
  public boolean existsUserContact(UUID userId, UUID contactId) {
    return userContactRepository.existsByUserIdAndContactId(userId, contactId);
  }

  @Transactional(readOnly = true)
  public List<UserContact> getUserContactsByContactId(UUID contactId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return userContactRepository.findByTenantIdAndContactId(tenantId, contactId);
  }
}
