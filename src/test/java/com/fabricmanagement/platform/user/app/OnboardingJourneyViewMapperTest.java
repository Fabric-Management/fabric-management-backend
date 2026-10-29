package com.fabricmanagement.platform.user.app;

import static org.assertj.core.api.Assertions.*;

import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import com.fabricmanagement.platform.lead.domain.CompanyEvidence;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.platform.user.domain.*;
import com.fabricmanagement.platform.user.dto.OnboardingCompanyCapability;
import java.util.*;
import org.junit.jupiter.api.Test;

class OnboardingJourneyViewMapperTest {
  private final OnboardingJourneyViewMapper mapper = new OnboardingJourneyViewMapper();

  @Test
  void anotherAuthorisedAdministratorCannotReadRegistrantMobileOrAcceptPersonalSteps() {
    UUID owner = UUID.randomUUID();
    var journey =
        OnboardingJourney.company(UUID.randomUUID(), owner, UUID.randomUUID(), "PLAYGROUND");
    var admin = member(journey.getTenantId(), UUID.randomUUID());
    var view = mapper.view(journey, admin, capability(true), evidence(), List.of());
    assertThat(view.acceptedValues().registrant().mobilePhone()).isNull();
    assertThat(view.acceptedValues().knownFields()).doesNotContain("mobilePhone");
    assertThat(view.personalMobilePhone()).isNull();
    assertThat(view.steps())
        .filteredOn(step -> step.stepId() == OnboardingStepId.YOUR_DETAILS)
        .allSatisfy(
            step -> {
              assertThat(step.allowed()).isFalse();
              assertThat(step.reason()).isEqualTo("ONBOARDING_REGISTRANT_REQUIRED");
            });
    assertThat(view.complete().allowed()).isFalse();
  }

  @Test
  void revokedOwnerSeesOwnJourneyMetadataButNoCompanySnapshotAndNoCompanyCapabilities() {
    UUID owner = UUID.randomUUID();
    var journey = OnboardingJourney.company(UUID.randomUUID(), owner, UUID.randomUUID(), "TRIAL");
    var view =
        mapper.view(
            journey,
            member(journey.getTenantId(), owner),
            capability(false),
            evidence(),
            List.of());
    assertThat(view.acceptedValues()).isNull();
    assertThat(view.save().allowed()).isFalse();
    assertThat(view.steps()).allSatisfy(step -> assertThat(step.allowed()).isFalse());
    assertThat(view.firstIncompleteStep()).isEqualTo(OnboardingStepId.ORGANIZATION_DETAILS);
  }

  private User member(UUID tenant, UUID id) {
    User user = User.create("Oliver", "Reed", UUID.randomUUID());
    user.setTenantId(tenant);
    user.setId(id);
    return user;
  }

  private OnboardingCompanyCapability capability(boolean allowed) {
    return new OnboardingCompanyCapability(
        PermissionKey.ORGANIZATION_CONFIGURE,
        allowed,
        allowed ? null : "ONBOARDING_COMPANY_PERMISSION_REQUIRED",
        null,
        false);
  }

  private CompanyEvidence evidence() {
    return new CompanyEvidence(
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
            "Eleanor", "Hughes", "eleanor.hughes@example.com", "+447700900123"),
        null,
        null,
        Set.of("organizationName", "mobilePhone"));
  }
}
