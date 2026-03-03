package com.fabricmanagement.common.platform.user.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.user.api.facade.UserFacade;
import com.fabricmanagement.common.platform.user.domain.User;
import com.fabricmanagement.common.platform.user.domain.event.UserDeactivatedEvent;
import com.fabricmanagement.common.platform.user.dto.CreateAdminUserRequest;
import com.fabricmanagement.common.platform.user.dto.CreateExternalUserRequest;
import com.fabricmanagement.common.platform.user.dto.CreateInternalUserRequest;
import com.fabricmanagement.common.platform.user.dto.UpdateUserProfileRequest;
import com.fabricmanagement.common.platform.user.dto.UpdateUserRequest;
import com.fabricmanagement.common.platform.user.dto.UserDto;
import com.fabricmanagement.common.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.human.core.employee.application.EmployeeService;
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
  private final EmployeeService employeeService;
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
  public List<UserDto> findByCompany(UUID tenantId, UUID companyId) {
    return userQueryService.findByCompany(tenantId, companyId);
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
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.info("Updating user: tenantId={}, userId={}", tenantId, userId);

    User user =
        userRepository
            .findByTenantIdAndId(tenantId, userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    user.updateProfile(request.getFirstName(), request.getLastName());
    User saved = userRepository.save(user);

    log.info("User updated: id={}, displayName={}", saved.getId(), saved.getDisplayName());
    return UserDto.from(saved, employeeService.getEmployeeByUserId(userId).orElse(null));
  }

  @Transactional
  public void deactivateUser(UUID userId, String reason) {
    UUID tenantId = TenantContext.getCurrentTenantId();
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
    return userOnboardingService.completeOnboarding(userId);
  }

  @Transactional
  public UserDto updateProfile(UUID userId, UpdateUserProfileRequest request, UUID requesterId) {
    return userProfileService.updateProfile(userId, request, requesterId);
  }
}
