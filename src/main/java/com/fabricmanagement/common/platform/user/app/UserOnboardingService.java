package com.fabricmanagement.common.platform.user.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.user.domain.User;
import com.fabricmanagement.common.platform.user.domain.event.UserOnboardingCompletedEvent;
import com.fabricmanagement.common.platform.user.dto.OnboardingStatusResponse;
import com.fabricmanagement.common.platform.user.dto.UserDto;
import com.fabricmanagement.common.platform.user.infra.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User onboarding service — status and completion.
 *
 * <p>Kept simple (single onboardingCompletedAt on User). Extensible later with step-based
 * onboarding if needed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserOnboardingService {

  private final UserRepository userRepository;
  private final DomainEventPublisher eventPublisher;

  @Transactional(readOnly = true)
  public OnboardingStatusResponse getStatus(UUID userId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.trace("Getting onboarding status: tenantId={}, userId={}", tenantId, userId);

    User user =
        userRepository
            .findByTenantIdAndId(tenantId, userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    boolean completed = user.getOnboardingCompletedAt() != null;
    Instant completedAt = user.getOnboardingCompletedAt();

    return OnboardingStatusResponse.builder()
        .hasCompletedOnboarding(completed)
        .completedAt(completedAt)
        .build();
  }

  @Transactional
  public UserDto completeOnboarding(UUID userId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.info("Completing onboarding: tenantId={}, userId={}", tenantId, userId);

    User user =
        userRepository
            .findByTenantIdAndId(tenantId, userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    if (user.getOnboardingCompletedAt() != null) {
      log.debug("User already completed onboarding: userId={}", userId);
      return UserDto.from(user);
    }

    user.completeOnboarding();
    User saved = userRepository.save(user);

    eventPublisher.publish(new UserOnboardingCompletedEvent(saved.getTenantId(), saved.getId()));

    log.info("Onboarding completed: userId={}, uid={}", saved.getId(), saved.getUid());

    return UserDto.from(saved);
  }

  @Transactional(readOnly = true)
  public boolean hasCompletedOnboarding(UUID userId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    User user =
        userRepository
            .findByTenantIdAndId(tenantId, userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
    return user.hasCompletedOnboarding();
  }
}
