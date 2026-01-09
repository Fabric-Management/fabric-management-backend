package com.fabricmanagement.common.platform.communication.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.communication.domain.AddressContact;
import com.fabricmanagement.common.platform.communication.domain.UserAddress;
import com.fabricmanagement.common.platform.communication.infra.repository.AddressRepository;
import com.fabricmanagement.common.platform.communication.infra.repository.UserAddressRepository;
import com.fabricmanagement.common.platform.user.infra.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User Address Service - Business logic for user-address assignments.
 *
 * <p>Handles Many-to-Many relationship between User and Address.
 *
 * <p>Key responsibilities:
 *
 * <ul>
 *   <li>Assign addresses to users
 *   <li>Remove user-address assignments
 *   <li>Manage primary address
 *   <li>Manage work address designation
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserAddressService {

  private final UserRepository userRepository;
  private final AddressRepository addressRepository;
  private final UserAddressRepository userAddressRepository;
  private final AddressContactService addressContactService;

  @Transactional(readOnly = true)
  public List<UserAddress> getUserAddresses(UUID userId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug("Finding user addresses: tenantId={}, userId={}", tenantId, userId);

    return userAddressRepository.findByTenantIdAndUserId(tenantId, userId);
  }

  @Transactional(readOnly = true)
  public Optional<UserAddress> getPrimaryAddress(UUID userId) {
    log.trace("Finding primary address: userId={}", userId);
    return userAddressRepository.findPrimaryByUserId(userId);
  }

  @Transactional(readOnly = true)
  public List<UserAddress> getWorkAddresses(UUID userId) {
    log.trace("Finding work addresses: userId={}", userId);
    return userAddressRepository.findWorkAddressesByUserId(userId);
  }

  @Transactional
  public UserAddress assignAddress(
      UUID userId, UUID addressId, Boolean isPrimary, Boolean isWorkAddress) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.info(
        "Assigning address to user: tenantId={}, userId={}, addressId={}, isPrimary={}, isWork={}",
        tenantId,
        userId,
        addressId,
        isPrimary,
        isWorkAddress);

    // Validate user exists
    userRepository
        .findByTenantIdAndId(tenantId, userId)
        .orElseThrow(() -> new IllegalArgumentException("User not found"));

    var address =
        addressRepository
            .findById(addressId)
            .orElseThrow(() -> new IllegalArgumentException("Address not found"));

    if (!address.getTenantId().equals(tenantId)) {
      throw new IllegalArgumentException("Address does not belong to current tenant");
    }

    if (userAddressRepository.findByUserIdAndAddressId(userId, addressId).isPresent()) {
      throw new IllegalArgumentException("Address is already assigned to this user");
    }

    // Set primary: remove primary flag from other addresses
    if (Boolean.TRUE.equals(isPrimary)) {
      userAddressRepository
          .findPrimaryByUserId(userId)
          .ifPresent(
              existing -> {
                existing.setIsPrimary(false);
                userAddressRepository.save(existing);
              });
    }

    UserAddress userAddress =
        UserAddress.builder()
            .userId(userId)
            .addressId(addressId)
            .isPrimary(isPrimary != null ? isPrimary : false)
            .isWorkAddress(isWorkAddress != null ? isWorkAddress : false)
            .build();

    return userAddressRepository.save(userAddress);
  }

  @Transactional
  public void removeAddress(UUID userId, UUID addressId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.info(
        "Removing address from user: tenantId={}, userId={}, addressId={}",
        tenantId,
        userId,
        addressId);

    UserAddress userAddress =
        userAddressRepository
            .findByUserIdAndAddressId(userId, addressId)
            .orElseThrow(() -> new IllegalArgumentException("Address assignment not found"));

    userAddressRepository.delete(userAddress);
  }

  @Transactional
  public UserAddress setAsPrimary(UUID userId, UUID addressId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.info(
        "Setting primary address: tenantId={}, userId={}, addressId={}",
        tenantId,
        userId,
        addressId);

    UserAddress userAddress =
        userAddressRepository
            .findByUserIdAndAddressId(userId, addressId)
            .orElseThrow(() -> new IllegalArgumentException("Address assignment not found"));

    // Remove primary from others
    userAddressRepository
        .findPrimaryByUserId(userId)
        .ifPresent(
            existing -> {
              if (!existing.getAddressId().equals(addressId)) {
                existing.setIsPrimary(false);
                userAddressRepository.save(existing);
              }
            });

    userAddress.setAsPrimary();
    return userAddressRepository.save(userAddress);
  }

  /** Get contacts for a user address. */
  @Transactional(readOnly = true)
  public List<AddressContact> getAddressContacts(UUID userId, UUID addressId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug(
        "Finding contacts for user address: tenantId={}, userId={}, addressId={}",
        tenantId,
        userId,
        addressId);

    // Verify address belongs to user
    userAddressRepository
        .findByUserIdAndAddressId(userId, addressId)
        .orElseThrow(() -> new IllegalArgumentException("Address is not assigned to this user"));

    return addressContactService.getAddressContacts(addressId);
  }

  /** Get primary contact for a user address. */
  @Transactional(readOnly = true)
  public Optional<AddressContact> getPrimaryContact(UUID userId, UUID addressId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.trace(
        "Finding primary contact for user address: tenantId={}, userId={}, addressId={}",
        tenantId,
        userId,
        addressId);

    // Verify address belongs to user
    userAddressRepository
        .findByUserIdAndAddressId(userId, addressId)
        .orElseThrow(() -> new IllegalArgumentException("Address is not assigned to this user"));

    return addressContactService.getPrimaryContact(addressId);
  }
}
