package com.fabricmanagement.platform.auth.app.onboarding;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.platform.auth.domain.port.SignupEvidencePort;
import com.fabricmanagement.platform.auth.domain.port.SignupEvidencePort.SignupCompany;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.platform.user.domain.port.OnboardingJourneyProvisioningPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CaptureSignupEvidenceStepTest {
  private final SignupEvidencePort evidence = mock(SignupEvidencePort.class);
  private final OnboardingJourneyProvisioningPort journeys =
      mock(OnboardingJourneyProvisioningPort.class);
  private final CaptureSignupEvidenceStep step = new CaptureSignupEvidenceStep(evidence, journeys);

  @Test
  void rawSignupIsCapturedWithoutCredentialsOrInventedCompanyContacts() throws Exception {
    var context = context();
    context.setRegistrationToken("setup-token-must-not-enter-evidence");
    context.setSetupUrl("https://fabric.example/setup?token=setup-token-must-not-enter-evidence");
    context.setOrganizationEmail("billing-only@example.com");
    step.execute(context);
    var saved = ArgumentCaptor.forClass(SignupCompany.class);
    verify(evidence).capture(saved.capture());
    assertThat(saved.getValue().facts().organisation().organizationName())
        .isEqualTo("Hawthorn Weaving Ltd");
    assertThat(saved.getValue().facts().contacts()).isNull();
    assertThat(saved.getValue().facts().knownFields())
        .doesNotContain("companyEmail", "mobilePhone");
    assertThat(new ObjectMapper().findAndRegisterModules().writeValueAsString(saved.getValue()))
        .doesNotContain("setup-token", "/setup?", "password", "billing-only");
    verify(journeys)
        .provision(context.getTenantId(), context.getUserId(), context.getSignupId(), "PLAYGROUND");
  }

  @Test
  void captureFailureCannotBeLoggedAsSuccessfulOrContinueProvisioning() {
    var context = context();
    doThrow(new PlatformDomainException("Injected failure", "LEAD_CAPTURE_FAILED", 503))
        .when(evidence)
        .capture(any());
    assertThatThrownBy(() -> step.execute(context)).isInstanceOf(PlatformDomainException.class);
    verifyNoInteractions(journeys);
  }

  @Test
  void provisioningDefaultIsNotInventedUserSelectionAndExistingIdentitiesAreStillCaptured() {
    var context = context();
    context.setSignupSelectedOS(null);
    context.setExistingIdentity(true);
    step.execute(context);
    var saved = ArgumentCaptor.forClass(SignupCompany.class);
    verify(evidence).capture(saved.capture());
    assertThat(saved.getValue().facts().selectedOS()).isNull();
    assertThat(saved.getValue().facts().knownFields()).doesNotContain("selectedOS");
    verify(journeys)
        .provision(context.getTenantId(), context.getUserId(), context.getSignupId(), "PLAYGROUND");
  }

  private OnboardingContext context() {
    var c = new OnboardingContext();
    c.setTenantId(UUID.randomUUID());
    c.setUserId(UUID.randomUUID());
    c.setOrganizationName("Hawthorn Weaving Ltd");
    c.setTaxId("GB123456789");
    c.setOrganizationType(OrganizationType.WEAVER);
    c.setAdminFirstName("Eleanor");
    c.setAdminLastName("Hughes");
    c.setAdminContact("eleanor.hughes@example.com");
    c.setSelectedOS(List.of("FabricOS"));
    c.setSignupSelectedOS(List.of("FabricOS"));
    c.setSignupIntent("PLAYGROUND");
    c.setDemoMode(true);
    return c;
  }
}
