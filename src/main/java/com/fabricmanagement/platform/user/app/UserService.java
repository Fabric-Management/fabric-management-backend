package com.fabricmanagement.platform.user.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.user.api.facade.UserFacade;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.domain.event.UserDeactivatedEvent;
import com.fabricmanagement.platform.user.domain.port.EmployeeProjectionPort;
import com.fabricmanagement.platform.user.dto.CompleteOnboardingRequest;
import com.fabricmanagement.platform.user.dto.CreateAdminUserRequest;
import com.fabricmanagement.platform.user.dto.CreateExternalUserRequest;
import com.fabricmanagement.platform.user.dto.CreateInternalUserRequest;
import com.fabricmanagement.platform.user.dto.UpdateUserProfileRequest;
import com.fabricmanagement.platform.user.dto.UpdateUserRequest;
import com.fabricmanagement.platform.user.dto.UserDto;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User Service — facade and lifecycle.
 *
 * <p>Implements {@link UserFacade} for cross-module communication. Delegates creation to {@link
 * UserCreationService}, queries to {@link UserQueryService}, onboarding to {@link
 * UserOnboardingService}, profile updates to {@link UserProfileService}. Keeps {@link #updateUser}
 * and {@link #deactivateUser} for simple lifecycle operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService implements UserFacade {

  private final UserCreationService userCreationService;
  private final UserQueryService userQueryService;
  private final UserOnboardingService userOnboardingService;
  private final UserProfileService userProfileService;
  private final UserRepository userRepository;
  private final EmployeeProjectionPort employeeProjectionPort;
  private final DomainEventPublisher eventPublisher;

  @Transactional
  public UserDto createInternalUser(CreateInternalUserRequest request) {
    return userCreationService.createInternalUser(request);
  }

  @Transactional
  public UserDto createExternalUser(CreateExternalUserRequest request) {
    return userCreationService.createExternalUser(request);
  }

  @Override
  @Transactional
  public UserDto createAdminUser(CreateAdminUserRequest request) {
    return userCreationService.createAdminUser(request);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UserDto> findById(UUID tenantId, UUID userId) {
    return userQueryService.findById(tenantId, userId);
  }

  @Transactional(readOnly = true)
  public Optional<UserDto> findByIdWithPermissionData(UUID tenantId, UUID userId) {
    return userQueryService.findByIdWithPermissionData(tenantId, userId);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UserDto> findByContactValue(String contactValue) {
    return userQueryService.findByContactValue(contactValue);
  }

  @Override
  @Transactional(readOnly = true)
  public List<UserDto> findByTenant(UUID tenantId) {
    return userQueryService.findByTenant(tenantId);
  }

  @Override
  @Transactional(readOnly = true)
  public List<UserDto> findByOrganization(UUID tenantId, UUID organizationId) {
    return userQueryService.findByOrganization(tenantId, organizationId);
  }

  @Transactional(readOnly = true)
  public List<UserDto> findByDepartments(UUID tenantId, java.util.Set<UUID> departmentIds) {
    return userQueryService.findByDepartments(tenantId, departmentIds);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean exists(UUID tenantId, UUID userId) {
    return userQueryService.exists(tenantId, userId);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean contactExists(String contactValue) {
    return userQueryService.contactExists(contactValue);
  }

  /** Tenant-scoped contact check (for enumeration-protected API). */
  @Transactional(readOnly = true)
  public boolean contactExistsInTenant(UUID tenantId, String contactValue) {
    return userQueryService.contactExistsInTenant(tenantId, contactValue);
  }

  @Transactional
  public UserDto updateUser(UUID userId, UpdateUserRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    log.info("Updating user: tenantId={}, userId={}", tenantId, userId);

    User user =
        userRepository
            .findByTenantIdAndId(tenantId, userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    user.updateProfile(request.getFirstName(), request.getLastName());
    User saved = userRepository.save(user);

    log.info("User updated: id={}, displayName={}", saved.getId(), saved.getDisplayName());
    return UserDto.from(saved, employeeProjectionPort.findByUserId(tenantId, userId).orElse(null));
  }

  @Transactional
  public void deactivateUser(UUID userId, String reason) {
    UUID tenantId = TenantContext.requireTenantId();
    deactivateUser(tenantId, userId, reason);
  }

  /**
   * TenantContext'e bağımlı olmayan, scheduler/background güvenli versiyon. Event listener veya
   * scheduled task gibi HTTP request context'i olmayan süreçlerden çağrılmalıdır.
   */
  @Transactional
  public void deactivateUser(UUID tenantId, UUID userId, String reason) {
    log.info("Deactivating user: tenantId={}, userId={}, reason={}", tenantId, userId, reason);

    User user =
        userRepository
            .findByTenantIdAndId(tenantId, userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    user.delete();
    userRepository.save(user);

    eventPublisher.publish(new UserDeactivatedEvent(tenantId, userId, reason));

    log.warn("User deactivated: id={}, uid={}", user.getId(), user.getUid());
  }

  @Transactional(readOnly = true)
  public boolean hasCompletedOnboarding(UUID userId) {
    return userOnboardingService.hasCompletedOnboarding(userId);
  }

  @Transactional
  public UserDto completeOnboarding(UUID userId) {
    return userOnboardingService.completeOnboarding(userId, null);
  }

  @Transactional
  public UserDto completeOnboarding(UUID userId, CompleteOnboardingRequest request) {
    return userOnboardingService.completeOnboarding(userId, request);
  }

  @Transactional
  public UserDto updateProfile(UUID userId, UpdateUserProfileRequest request, UUID requesterId) {
    return userProfileService.updateProfile(userId, request, requesterId);
  }
}
