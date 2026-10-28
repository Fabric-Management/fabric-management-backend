package com.fabricmanagement.platform.user.app;

import com.fabricmanagement.platform.user.domain.OnboardingJourney;
import com.fabricmanagement.platform.user.domain.port.OnboardingJourneyProvisioningPort;
import com.fabricmanagement.platform.user.infra.repository.OnboardingJourneyRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OnboardingJourneyProvisioningService implements OnboardingJourneyProvisioningPort {
  private final OnboardingJourneyRepository journeys;

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void provision(UUID tenantId, UUID ownerId, UUID signupId, String intent) {
    var existing = journeys.findByTenantIdAndOwnerUserId(tenantId, ownerId);
    if (existing.isEmpty())
      journeys.save(OnboardingJourney.company(tenantId, ownerId, signupId, intent));
  }
}
