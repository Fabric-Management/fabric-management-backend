package com.fabricmanagement.platform.user.domain.port;

import com.fabricmanagement.platform.lead.domain.CompanyEvidence;
import com.fabricmanagement.platform.user.domain.OnboardingStepId;
import java.time.Instant;
import java.util.UUID;

/**
 * No public Lead lookup: authoritative tenant/signup linkage is supplied by a persisted journey.
 */
public interface OnboardingEvidencePort {
  CompanyEvidence current(UUID tenantId, UUID signupId);

  void accept(
      UUID tenantId,
      UUID signupId,
      UUID actor,
      UUID operationId,
      OnboardingStepId step,
      CompanyEvidence evidence,
      Instant completedAt);

  void correctLegacy(
      UUID tenantId,
      UUID signupId,
      UUID actor,
      UUID operationId,
      CompanyEvidence evidence,
      Instant firstCompletionAt);
}
