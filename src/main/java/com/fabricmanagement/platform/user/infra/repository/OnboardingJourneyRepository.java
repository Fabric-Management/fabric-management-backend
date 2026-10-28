package com.fabricmanagement.platform.user.infra.repository;

import com.fabricmanagement.platform.user.domain.OnboardingJourney;
import com.fabricmanagement.platform.user.domain.OnboardingJourney.Kind;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface OnboardingJourneyRepository extends JpaRepository<OnboardingJourney, UUID> {
  Optional<OnboardingJourney> findByTenantIdAndKind(UUID tenantId, Kind kind);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<OnboardingJourney> findLockedByTenantIdAndKind(UUID tenantId, Kind kind);

  Optional<OnboardingJourney> findByTenantIdAndOwnerUserId(UUID tenantId, UUID userId);
}
