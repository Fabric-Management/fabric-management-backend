package com.fabricmanagement.testsupport;

import com.fabricmanagement.platform.auth.domain.port.SignupEvidencePort;
import com.fabricmanagement.platform.auth.domain.port.SignupEvidencePort.SignupCompany;
import com.fabricmanagement.platform.lead.domain.CompanyEvidence;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.platform.user.domain.OnboardingJourney;
import com.fabricmanagement.platform.user.infra.repository.OnboardingJourneyRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** A company already set up by another member; the tested user's completion remains independent. */
public final class CompletedCompanyOnboardingFixture {
  private CompletedCompanyOnboardingFixture() {}

  public static void create(
      UUID tenant,
      UUID owner,
      SignupEvidencePort capture,
      OnboardingJourneyRepository journeys,
      PlatformTransactionManager transactions) {
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            tx -> {
              UUID signup = UUID.randomUUID();
              var evidence =
                  new CompanyEvidence(
                      new CompanyEvidence.Organisation(
                          "Hawthorn Weaving Ltd",
                          "GB123456789",
                          OrganizationType.WEAVER,
                          null,
                          null,
                          null,
                          null,
                          null),
                      null,
                      new CompanyEvidence.Registrant(
                          "Oliver", "Reed", "oliver.reed@example.com", null),
                      null,
                      List.of("FabricOS"),
                      Set.of(
                          "organizationName",
                          "taxId",
                          "organizationType",
                          "firstName",
                          "lastName",
                          "workEmail",
                          "selectedOS"));
              capture.capture(new SignupCompany(signup, tenant, owner, "TRIAL", evidence, null));
              var journey = OnboardingJourney.company(tenant, owner, signup, "TRIAL");
              for (var step : journey.steps()) journey.accept(step, UUID.randomUUID(), true);
              journey.finish(journey.getReviewOperationId());
              journeys.saveAndFlush(journey);
            });
  }
}
