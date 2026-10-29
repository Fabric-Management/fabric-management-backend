package com.fabricmanagement.platform.user.infra.repository;

import com.fabricmanagement.platform.user.domain.OnboardingOperation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OnboardingOperationRepository extends JpaRepository<OnboardingOperation, UUID> {
  Optional<OnboardingOperation> findByTenantIdAndJourneyIdAndOperationId(
      UUID tenant, UUID journey, UUID operation);
}
