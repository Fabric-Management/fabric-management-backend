package com.fabricmanagement.common.platform.user.app;

import com.fabricmanagement.common.infrastructure.assignment.BaseAssignmentService;
import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.communication.domain.Address;
import com.fabricmanagement.common.platform.communication.infra.repository.AddressRepository;
import com.fabricmanagement.common.platform.user.domain.UserAddress;
import com.fabricmanagement.common.platform.user.domain.event.AddressAssignedEvent;
import com.fabricmanagement.common.platform.user.infra.repository.UserAddressRepository;
import com.fabricmanagement.common.platform.user.infra.repository.UserRepository;
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

  public UserAddressAssignmentService(
      UserRepository userRepository,
      AddressRepository addressRepository,
      UserAddressRepository userAddressRepository,
      DomainEventPublisher eventPublisher) {
    this.userRepository = userRepository;
    this.addressRepository = addressRepository;
    this.userAddressRepository = userAddressRepository;
    this.eventPublisher = eventPublisher;
  }

  @Override
  protected void onAfterAssign(UserAddress junction) {
    eventPublisher.publish(
        new AddressAssignedEvent(
            TenantContext.getCurrentTenantId(), junction.getUserId(), junction.getAddressId()));
  }

  @Override
  protected JpaRepository<UserAddress, ?> getRepository() {
    return userAddressRepository;
  }

  @Override
  protected void validateParentExists(UUID parentId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    userRepository
        .findByTenantIdAndId(tenantId, parentId)
        .orElseThrow(() -> new IllegalArgumentException("User not found"));
  }

  @Override
  protected void validateChildExists(UUID childId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    Address address =
        addressRepository
            .findById(childId)
            .orElseThrow(() -> new IllegalArgumentException("Address not found"));
    if (!address.getTenantId().equals(tenantId)) {
      throw new IllegalArgumentException("Address does not belong to current tenant");
    }
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
    UUID tenantId = TenantContext.getCurrentTenantId();
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
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.info(
        "Assigning address to user: userId={}, addressId={}, isPrimary={}, isWork={}",
        userId,
        addressId,
        isPrimary,
        isWorkAddress);

    validateParentExists(userId);
    validateChildExists(addressId);
    validateAssignment(userId, addressId);

    if (userAddressRepository.findByUserIdAndAddressId(userId, addressId).isPresent()) {
      throw new IllegalArgumentException("Address is already assigned to this user");
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
