package com.fabricmanagement.platform.user.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.communication.domain.ContactType;
import com.fabricmanagement.platform.organization.app.CompanyOnboardingProfileService;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.domain.event.UserOnboardingCompletedEvent;
import com.fabricmanagement.platform.user.domain.port.EmployeeProjectionPort;
import com.fabricmanagement.platform.user.dto.CompleteOnboardingRequest;
import com.fabricmanagement.platform.user.dto.OnboardingStatusResponse;
import com.fabricmanagement.platform.user.dto.UserDto;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.platform.user.security.OnboardingAccessService;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User onboarding service — status and completion.
 *
 * <p>Supports two call modes:
 *
 * <ol>
 *   <li>Legacy — {@code completeOnboarding(userId, null)}: marks user as onboarded, no enrichment.
 *   <li>Enriched — {@code completeOnboarding(userId, request)}: marks user as onboarded AND
 *       persists company profile data (legalName, industry, address, etc.) to the root
 *       Organization.
 * </ol>
 *
 * Registered company owners use the accepted six-step journey; this legacy route remains for
 * personal callers and corrections after an already completed company journey.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserOnboardingService {

  private final UserRepository userRepository;
  private final EmployeeProjectionPort employeeProjectionPort;
  private final DomainEventPublisher eventPublisher;
  private final CompanyOnboardingProfileService companyProfileService;
  private final OnboardingCommandLockService commandLockService;
  private final OnboardingRequestValidator requestValidator;
  private final UserProfileService userProfileService;
  private final OnboardingAccessService onboardingAccessService;
  private final OnboardingLegacyRetentionService legacyRetention;

  @Transactional(readOnly = true)
  public OnboardingStatusResponse getStatus(UUID userId) {
    UUID tenantId = TenantContext.requireTenantId();
    log.trace("Getting onboarding status: tenantId={}, userId={}", tenantId, userId);

    User user = onboardingAccessService.requireCurrentUser(userId);

    boolean completed = user.getOnboardingCompletedAt() != null;
    Instant completedAt = user.getOnboardingCompletedAt();

    return OnboardingStatusResponse.builder()
        .hasCompletedOnboarding(completed)
        .completedAt(completedAt)
        .companySetup(onboardingAccessService.companySetupCapability(user))
        .build();
  }

  /**
   * Complete onboarding and optionally enrich the tenant's root organization.
   *
   * @param userId Authenticated user UUID
   * @param request Enrichment data (may be null — skips enrichment, marks completion only)
   * @return Updated UserDto
   */
  @Transactional
  public UserDto completeOnboarding(UUID userId, CompleteOnboardingRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    log.info("Completing onboarding: tenantId={}, userId={}", tenantId, userId);

    User user = onboardingAccessService.requireCurrentUser(userId);
    boolean companyChanges = request != null && request.hasCompanyChanges();
    commandLockService.lockAndRefresh(user);
    // Check company authority only after the wait: evaluating it before locking could retain
    // permission entities in the persistence context while another transaction revokes them.
    user = onboardingAccessService.requireCurrentUser(userId);
    if (companyChanges) onboardingAccessService.requireCompanySetup(user);
    requestValidator.validate(request);
    var retainedJourney = legacyRetention.prepare(user, request);
    var profile = companyChanges ? companyProfileService.prepare(request) : null;
    if (request != null) saveUserMobile(userId, request);
    if (profile != null) companyProfileService.save(profile);
    legacyRetention.retain(retainedJourney, user, request);

    UserDto completed = markCompleted(user, tenantId);
    legacyRetention.completePersonal(user);
    return completed;
  }

  /** Only the accepted-journey orchestrator in this package calls this after validating review. */
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
  UserDto completeAcceptedJourney(UUID userId) {
    User user = onboardingAccessService.requireCurrentUser(userId);
    return markCompleted(user, user.getTenantId());
  }

  private UserDto markCompleted(User user, UUID tenantId) {
    UUID userId = user.getId();

    if (user.hasCompletedOnboarding()) {
      return UserDto.from(user, employeeProjectionPort.findByUserId(tenantId, userId).orElse(null));
    }

    user.completeOnboarding();
    User saved = userRepository.save(user);

    eventPublisher.publish(new UserOnboardingCompletedEvent(saved.getTenantId(), saved.getId()));

    log.info("Onboarding completed: userId={}, uid={}", saved.getId(), saved.getUid());

    return UserDto.from(
        saved, employeeProjectionPort.findByUserId(tenantId, saved.getId()).orElse(null));
  }

  @Transactional(readOnly = true)
  public boolean hasCompletedOnboarding(UUID userId) {
    UUID tenantId = TenantContext.requireTenantId();
    User user =
        userRepository
            .findByTenantIdAndId(tenantId, userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
    return user.hasCompletedOnboarding();
  }

  private void saveUserMobile(UUID userId, CompleteOnboardingRequest request) {
    if (request.getMobilePhone() != null) {
      userProfileService.updatePersonalContact(
          userId, request.getMobilePhone().trim(), ContactType.MOBILE);
    }
  }
}
