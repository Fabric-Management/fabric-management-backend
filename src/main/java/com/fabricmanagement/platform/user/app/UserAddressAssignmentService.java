package com.fabricmanagement.platform.user.app;

import com.fabricmanagement.common.infrastructure.assignment.BaseAssignmentService;
import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.communication.app.AddressService;
import com.fabricmanagement.platform.communication.app.ContactDetailsOwnership;
import com.fabricmanagement.platform.communication.domain.Address;
import com.fabricmanagement.platform.communication.dto.UpdateAddressRequest;
import com.fabricmanagement.platform.communication.infra.repository.AddressRepository;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationAddressRepository;
import com.fabricmanagement.platform.user.domain.UserAddress;
import com.fabricmanagement.platform.user.domain.event.AddressAssignedEvent;
import com.fabricmanagement.platform.user.infra.repository.UserAddressRepository;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.platform.user.security.UserContactDetailsAccess;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User–Address assignment service. Extends {@link BaseAssignmentService}; adds isWorkAddress and
 * assign overload, getWorkAddresses.
 */
@Service
@Slf4j
public class UserAddressAssignmentService extends BaseAssignmentService<UserAddress> {

  private final UserRepository userRepository;
  private final AddressRepository addressRepository;
  private final UserAddressRepository userAddressRepository;
  private final DomainEventPublisher eventPublisher;
  private final ContactDetailsOwnership ownership;
  private final UserContactDetailsAccess access;
  private final AddressService addresses;
  private final OrganizationAddressRepository organisationAddresses;

  public UserAddressAssignmentService(
      UserRepository userRepository,
      AddressRepository addressRepository,
      UserAddressRepository userAddressRepository,
      DomainEventPublisher eventPublisher,
      ContactDetailsOwnership ownership,
      UserContactDetailsAccess access,
      AddressService addresses,
      OrganizationAddressRepository organisationAddresses) {
    this.userRepository = userRepository;
    this.addressRepository = addressRepository;
    this.userAddressRepository = userAddressRepository;
    this.eventPublisher = eventPublisher;
    this.ownership = ownership;
    this.access = access;
    this.addresses = addresses;
    this.organisationAddresses = organisationAddresses;
  }

  @Override
  protected void onAfterAssign(UserAddress junction) {
    eventPublisher.publish(
        new AddressAssignedEvent(
            TenantContext.requireTenantId(), junction.getUserId(), junction.getAddressId()));
  }

  @Override
  protected JpaRepository<UserAddress, ?> getRepository() {
    return userAddressRepository;
  }

  @Override
  protected void validateParentExists(UUID parentId) {
    UUID tenantId = TenantContext.requireTenantId();
    userRepository
        .findByTenantIdAndId(tenantId, parentId)
        .orElseThrow(() -> new PlatformDomainException("User not found", "USER_NOT_FOUND", 404));
  }

  @Override
  protected void validateChildExists(UUID childId) {
    UUID tenantId = TenantContext.requireTenantId();
    lockAddress(childId);
  }

  private Address lockAddress(UUID id) {
    return addressRepository
        .lockActiveByTenantAndId(TenantContext.requireTenantId(), id)
        .orElseThrow(
            () -> new PlatformDomainException("Address not found", "USER_ADDRESS_NOT_FOUND", 404));
  }

  @Override
  protected Optional<UserAddress> findExisting(UUID parentId, UUID childId) {
    return userAddressRepository.findByUserIdAndAddressId(parentId, childId);
  }

  @Override
  protected Optional<UserAddress> findPrimaryByParent(UUID parentId) {
    return userAddressRepository.findPrimaryByUserId(parentId);
  }

  @Override
  protected List<UserAddress> findByParent(UUID parentId) {
    UUID tenantId = TenantContext.requireTenantId();
    return userAddressRepository.findByTenantIdAndUserId(tenantId, parentId);
  }

  @Override
  protected UserAddress buildJunction(UUID parentId, UUID childId, Boolean primaryFlag) {
    return UserAddress.builder()
        .userId(parentId)
        .addressId(childId)
        .isPrimary(Boolean.TRUE.equals(primaryFlag))
        .isWorkAddress(false)
        .build();
  }

  @Transactional
  public UserAddress assignAddress(
      UUID userId, UUID addressId, Boolean isPrimary, Boolean isWorkAddress) {
    validateParentExists(userId);
    validateChildExists(addressId);
    access.requireWrite(userId);
    ownership.requireAddressReuse(addressId, true);
    return saveAssignment(userId, addressId, isPrimary, isWorkAddress);
  }

  @Transactional
  UserAddress assignAddressForProvisioning(
      UUID userId, UUID addressId, Boolean isPrimary, Boolean isWorkAddress) {
    validateParentExists(userId);
    validateChildExists(addressId);
    return saveAssignment(userId, addressId, isPrimary, isWorkAddress);
  }

  private UserAddress saveAssignment(
      UUID userId, UUID addressId, Boolean isPrimary, Boolean isWorkAddress) {
    log.info(
        "Assigning address to user: userId={}, addressId={}, isPrimary={}, isWork={}",
        userId,
        addressId,
        isPrimary,
        isWorkAddress);

    validateAssignment(userId, addressId);

    if (userAddressRepository.findByUserIdAndAddressId(userId, addressId).isPresent()) {
      throw new PlatformDomainException(
          "Address is already assigned to this user", "USER_ADDRESS_ALREADY_ASSIGNED", 409);
    }

    if (Boolean.TRUE.equals(isPrimary)) {
      userAddressRepository
          .findPrimaryByUserId(userId)
          .ifPresent(
              existing -> {
                existing.setIsPrimary(false);
                userAddressRepository.save(existing);
              });
    }

    UserAddress junction =
        UserAddress.builder()
            .userId(userId)
            .addressId(addressId)
            .isPrimary(isPrimary != null ? isPrimary : false)
            .isWorkAddress(isWorkAddress != null ? isWorkAddress : false)
            .build();
    UserAddress saved = userAddressRepository.save(junction);
    onAfterAssign(saved);
    return saved;
  }

  @Transactional
  public void removeAddress(UUID userId, UUID addressId) {
    unassign(userId, addressId);
  }

  @Transactional
  public UserAddress setAsPrimary(UUID userId, UUID addressId) {
    return setPrimary(userId, addressId);
  }

  @Transactional
  public Address updateAddress(UUID userId, UUID addressId, UpdateAddressRequest request) {
    var original = lockAddress(addressId);
    var link = requireLink(userId, addressId);
    access.requireWrite(userId);
    if (!hasOtherOwners(userId, addressId)) {
      return addresses.updateAddress(addressId, request);
    }
    var replacement = addresses.copyForUpdate(original, request);
    userAddressRepository.delete(link);
    var replacementLink =
        userAddressRepository.save(
            UserAddress.builder()
                .userId(userId)
                .addressId(replacement.getId())
                .isPrimary(link.getIsPrimary())
                .isWorkAddress(link.getIsWorkAddress())
                .build());
    onAfterAssign(replacementLink);
    return replacement;
  }

  private boolean hasOtherOwners(UUID userId, UUID addressId) {
    UUID tenant = TenantContext.requireTenantId();
    return organisationAddresses.existsByTenantIdAndAddressIdAndIsActiveTrue(tenant, addressId)
        || userAddressRepository
            .findByTenantIdAndAddressIdAndIsActiveTrue(tenant, addressId)
            .stream()
            .anyMatch(link -> !userId.equals(link.getUserId()));
  }

  private UserAddress requireLink(UUID userId, UUID addressId) {
    return findExisting(userId, addressId)
        .filter(link -> TenantContext.requireTenantId().equals(link.getTenantId()))
        .filter(link -> Boolean.TRUE.equals(link.getIsActive()))
        .orElseThrow(
            () ->
                new PlatformDomainException(
                    "Address assignment not found", "USER_ADDRESS_ASSIGNMENT_NOT_FOUND", 404));
  }

  @Override
  @Transactional
  public UserAddress assign(UUID userId, UUID addressId, Boolean primary) {
    return assignAddress(userId, addressId, primary, false);
  }

  @Override
  @Transactional
  public UserAddress setPrimary(UUID userId, UUID addressId) {
    requireLink(userId, addressId);
    access.requireWrite(userId);
    return super.setPrimary(userId, addressId);
  }

  @Override
  @Transactional
  public void unassign(UUID userId, UUID addressId) {
    lockAddress(addressId);
    var link = requireLink(userId, addressId);
    access.requireWrite(userId);
    userAddressRepository.delete(link);
    if (!hasOtherOwners(userId, addressId)) {
      addresses.deleteAddress(addressId);
    }
  }

  @Transactional(readOnly = true)
  public List<UserAddress> getUserAddresses(UUID userId) {
    return getByParent(userId);
  }

  @Transactional(readOnly = true)
  public Optional<UserAddress> getPrimaryAddress(UUID userId) {
    return getPrimary(userId);
  }

  @Transactional(readOnly = true)
  public List<UserAddress> getWorkAddresses(UUID userId) {
    return userAddressRepository.findWorkAddressesByUserId(userId);
  }
}
