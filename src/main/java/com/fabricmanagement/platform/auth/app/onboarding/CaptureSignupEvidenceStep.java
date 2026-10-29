package com.fabricmanagement.platform.auth.app.onboarding;

import com.fabricmanagement.platform.auth.domain.port.SignupEvidencePort;
import com.fabricmanagement.platform.auth.domain.port.SignupEvidencePort.SignupCompany;
import com.fabricmanagement.platform.lead.domain.CompanyEvidence;
import com.fabricmanagement.platform.user.domain.port.OnboardingJourneyProvisioningPort;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Capture raw registration facts before demo fixtures; failure aborts the entire signup. */
@Component
@Order(6)
@RequiredArgsConstructor
public class CaptureSignupEvidenceStep implements OnboardingStep {
  private final SignupEvidencePort evidence;
  private final OnboardingJourneyProvisioningPort journeys;

  @Override
  public void execute(OnboardingContext context) {
    var known =
        new HashSet<>(
            Set.of("organizationName", "organizationType", "firstName", "lastName", "workEmail"));
    if (context.getTaxId() != null) known.add("taxId");
    if (context.getSignupSelectedOS() != null) known.add("selectedOS");
    CompanyEvidence.Headquarters address = null;
    CompanyEvidence.CompanyContacts contacts = null;
    if (context.isSalesLed()) {
      address =
          new CompanyEvidence.Headquarters(
              context.getAddress(),
              null,
              context.getCity(),
              context.getState(),
              context.getDistrict(),
              context.getPostalCode(),
              context.getCountry(),
              null);
      contacts =
          new CompanyEvidence.CompanyContacts(
              context.getOrganizationEmail(), context.getPhoneNumber());
      if (context.getAddress() != null) known.add("addressLine1");
      if (context.getCity() != null) known.add("city");
      if (context.getCountry() != null) known.add("country");
      if (context.getState() != null) known.add("state");
      if (context.getDistrict() != null) known.add("district");
      if (context.getPostalCode() != null) known.add("postalCode");
      if (context.getOrganizationEmail() != null) known.add("companyEmail");
      if (context.getPhoneNumber() != null) known.add("companyPhone");
    }
    var snapshot =
        new CompanyEvidence(
            new CompanyEvidence.Organisation(
                context.getOrganizationName(),
                context.getTaxId(),
                context.getOrganizationType(),
                null,
                null,
                null,
                null,
                null),
            address,
            new CompanyEvidence.Registrant(
                context.getAdminFirstName(),
                context.getAdminLastName(),
                context.getAdminContact(),
                null),
            contacts,
            context.getSignupSelectedOS(),
            known);
    evidence.capture(
        new SignupCompany(
            context.getSignupId(),
            context.getTenantId(),
            context.getUserId(),
            context.getSignupIntent(),
            snapshot,
            context.getTermsAcceptedAt()));
    journeys.provision(
        context.getTenantId(),
        context.getUserId(),
        context.getSignupId(),
        context.getSignupIntent());
  }
}
