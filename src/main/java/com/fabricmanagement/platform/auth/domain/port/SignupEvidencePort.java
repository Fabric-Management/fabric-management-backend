package com.fabricmanagement.platform.auth.domain.port;

import com.fabricmanagement.platform.lead.domain.CompanyEvidence;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Narrow credential-free contract; capture joins the existing signup transaction. */
public interface SignupEvidencePort {
  void capture(SignupCompany signup);

  /**
   * Records, or refreshes, the lead of a signup form whose e-mail link has not been used yet. The
   * lead stays unverified until {@link #capture} links it to its tenant. Needs the caller's
   * transaction.
   */
  void recordUnverified(UnverifiedSignup signup);

  /** What the signup form told us; {@code signupId} is the pending signup's id. */
  record UnverifiedSignup(
      UUID signupId,
      String organizationName,
      String taxId,
      OrganizationType organizationType,
      String firstName,
      String lastName,
      String workEmail,
      List<String> selectedOS,
      String intent) {}

  record SignupCompany(
      UUID signupId,
      UUID tenantId,
      UUID ownerUserId,
      String intent,
      CompanyEvidence facts,
      Instant termsAcceptedAt) {}
}
