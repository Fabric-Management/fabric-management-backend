package com.fabricmanagement.common.platform.user.app;

import com.fabricmanagement.common.platform.user.dto.UserDto;
import com.fabricmanagement.common.platform.user.infra.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User query service — read-only operations with optional caching.
 *
 * <p>Used by UserFacade and controllers for listing and lookup. Cache keys are tenant/company
 * scoped; invalidation is handled by {@link UserCacheInvalidationService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserQueryService {

  private final UserRepository userRepository;

  @Transactional(readOnly = true)
  public Optional<UserDto> findById(UUID tenantId, UUID userId) {
    log.debug("Finding user: tenantId={}, userId={}", tenantId, userId);
    return userRepository.findByTenantIdAndId(tenantId, userId).map(UserDto::from);
  }

  @Transactional(readOnly = true)
  public Optional<UserDto> findByContactValue(String contactValue) {
    log.debug("Finding user by contact: contactValue=(masked)");
    return userRepository.findByContactValue(contactValue).map(UserDto::from);
  }

  @Transactional(readOnly = true)
  @Cacheable(value = "users-by-tenant", key = "#tenantId.toString()")
  public List<UserDto> findByTenant(UUID tenantId) {
    log.debug("Finding users by tenant: tenantId={}", tenantId);
    return userRepository.findByTenantIdAndIsActiveTrue(tenantId).stream()
        .map(UserDto::from)
        .toList();
  }

  @Transactional(readOnly = true)
  @Cacheable(value = "users-by-company", key = "#tenantId.toString() + '-' + #companyId.toString()")
  public List<UserDto> findByCompany(UUID tenantId, UUID companyId) {
    log.debug("Finding users by company: tenantId={}, companyId={}", tenantId, companyId);
    return userRepository
        .findByTenantIdAndOrganizationIdAndIsActiveTrue(tenantId, companyId)
        .stream()
        .map(UserDto::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public boolean exists(UUID tenantId, UUID userId) {
    return userRepository.existsByTenantIdAndId(tenantId, userId);
  }

  /**
   * Check if contact value exists in tenant (tenant-scoped — for enumeration protection).
   *
   * @param tenantId Tenant ID
   * @param contactValue Contact value (email or phone)
   * @return true if any user in tenant has this contact value
   */
  @Transactional(readOnly = true)
  public boolean contactExistsInTenant(UUID tenantId, String contactValue) {
    log.trace("Checking contact exists in tenant: tenantId={}", tenantId);
    return userRepository.existsByTenantIdAndContactValue(tenantId, contactValue);
  }

  /** Global contact exists (any tenant) — use only where cross-tenant check is required. */
  @Transactional(readOnly = true)
  public boolean contactExists(String contactValue) {
    return userRepository.existsByContactValue(contactValue);
  }
}
