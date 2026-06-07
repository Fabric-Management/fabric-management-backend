package com.fabricmanagement.platform.user.app;

import com.fabricmanagement.common.infrastructure.assignment.BaseAssignmentService;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.organization.domain.OrganizationAddress;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationAddressRepository;
import com.fabricmanagement.platform.user.domain.UserWorkLocation;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.platform.user.infra.repository.UserWorkLocationRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class UserWorkLocationService extends BaseAssignmentService<UserWorkLocation> {

  private final UserRepository userRepository;
  private final OrganizationAddressRepository organizationAddressRepository;
  private final UserWorkLocationRepository userWorkLocationRepository;

  public UserWorkLocationService(
      UserRepository userRepository,
      OrganizationAddressRepository organizationAddressRepository,
      UserWorkLocationRepository userWorkLocationRepository) {
    this.userRepository = userRepository;
    this.organizationAddressRepository = organizationAddressRepository;
    this.userWorkLocationRepository = userWorkLocationRepository;
  }

  @Override
  protected JpaRepository<UserWorkLocation, ?> getRepository() {
    return userWorkLocationRepository;
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
    OrganizationAddress oa =
        organizationAddressRepository
            .findByAddressId(childId)
            .orElseThrow(
                () ->
                    new PlatformDomainException(
                        "Organization address not found", "USER_LOCATION_NOT_FOUND", 404));

    if (!oa.getTenantId().equals(tenantId)) {
      throw new PlatformDomainException(
          "Organization address does not belong to current tenant",
          "USER_LOCATION_TENANT_MISMATCH",
          403);
    }
  }

  @Override
  protected Optional<UserWorkLocation> findExisting(UUID parentId, UUID childId) {
    return userWorkLocationRepository.findByUserIdAndOrgAddressId(parentId, childId);
  }

  @Override
  protected Optional<UserWorkLocation> findPrimaryByParent(UUID parentId) {
    return userWorkLocationRepository.findPrimaryByUserId(parentId);
  }

  @Override
  protected List<UserWorkLocation> findByParent(UUID parentId) {
    UUID tenantId = TenantContext.requireTenantId();
    return userWorkLocationRepository.findByTenantIdAndUserId(tenantId, parentId);
  }

  @Override
  protected UserWorkLocation buildJunction(UUID parentId, UUID childId, Boolean primaryFlag) {
    return UserWorkLocation.builder()
        .userId(parentId)
        .orgAddressId(childId)
        .isPrimary(Boolean.TRUE.equals(primaryFlag))
        .build();
  }

  @Transactional
  public UserWorkLocation assignLocation(
      UUID userId, UUID orgAddressId, Boolean isPrimary, String notes) {
    log.info(
        "Assigning work location: userId={}, orgAddressId={}, isPrimary={}",
        userId,
        orgAddressId,
        isPrimary);
    validateParentExists(userId);
    validateChildExists(orgAddressId);

    if (userWorkLocationRepository.findByUserIdAndOrgAddressId(userId, orgAddressId).isPresent()) {
      throw new PlatformDomainException(
          "Work location is already assigned to this user", "USER_LOCATION_ALREADY_ASSIGNED", 409);
    }

    if (Boolean.TRUE.equals(isPrimary)) {
      userWorkLocationRepository
          .findPrimaryByUserId(userId)
          .ifPresent(
              existing -> {
                existing.setIsPrimary(false);
                userWorkLocationRepository.save(existing);
              });
    }

    UserWorkLocation wl =
        UserWorkLocation.builder()
            .userId(userId)
            .orgAddressId(orgAddressId)
            .isPrimary(isPrimary != null ? isPrimary : false)
            .notes(notes)
            .build();

    return userWorkLocationRepository.save(wl);
  }

  @Transactional
  public void removeLocation(UUID userId, UUID orgAddressId) {
    unassign(userId, orgAddressId);
  }

  @Transactional(readOnly = true)
  public List<UserWorkLocation> getUserLocations(UUID userId) {
    return getByParent(userId);
  }

  @Transactional(readOnly = true)
  public Optional<UserWorkLocation> getPrimaryLocation(UUID userId) {
    return getPrimary(userId);
  }

  @Transactional(readOnly = true)
  public List<UserWorkLocation> getLocationUsers(UUID orgAddressId) {
    return userWorkLocationRepository.findByOrgAddressId(orgAddressId);
  }

  /**
   * Batch-fetch primary work locations for a list of users. Returns a map from userId to the
   * address label of their primary work location.
   */
  @Transactional(readOnly = true)
  public Map<UUID, String> getPrimaryLocationLabels(UUID tenantId, List<UUID> userIds) {
    if (userIds == null || userIds.isEmpty()) {
      return Map.of();
    }
    List<UserWorkLocation> primaries =
        userWorkLocationRepository.findPrimaryByTenantIdAndUserIdIn(tenantId, userIds);
    Map<UUID, String> result = new java.util.HashMap<>();
    for (UserWorkLocation wl : primaries) {
      if (wl.getOrganizationAddress() != null
          && wl.getOrganizationAddress().getAddress() != null
          && wl.getOrganizationAddress().getAddress().getLabel() != null) {
        result.put(wl.getUserId(), wl.getOrganizationAddress().getAddress().getLabel());
      }
    }
    return result;
  }
}
