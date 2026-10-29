package com.fabricmanagement.platform.user.domain.port;

import java.util.UUID;

/** Internal registration bootstrap only; request identity is never accepted by journey commands. */
public interface OnboardingJourneyProvisioningPort {
  void provision(UUID tenantId, UUID ownerId, UUID signupId, String intent);
}
