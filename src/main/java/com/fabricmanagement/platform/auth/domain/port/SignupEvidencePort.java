package com.fabricmanagement.platform.auth.domain.port;

import com.fabricmanagement.platform.lead.domain.CompanyEvidence;
import java.time.Instant;
import java.util.UUID;

/** Narrow credential-free contract; capture joins the existing signup transaction. */
public interface SignupEvidencePort {
  void capture(SignupCompany signup);

  record SignupCompany(
      UUID signupId,
      UUID tenantId,
      UUID ownerUserId,
      String intent,
      CompanyEvidence facts,
      Instant termsAcceptedAt) {}
}
