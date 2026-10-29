package com.fabricmanagement.platform.lead.domain;

import com.fabricmanagement.platform.organization.domain.OrganizationType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Set;

/**
 * Credential-free evidence. A known field with null means explicitly cleared; unknown is not
 * absent.
 */
@Schema(
    name = "OnboardingAcceptedCompanyEvidence",
    description =
        "Credential-free evidence."
            + " knownFields distinguishes unknown information from an explicit cleared null."
            + " Signup observations never imply paid conversion or accepted onboarding steps.")
public record CompanyEvidence(
    Organisation organisation,
    @Schema(nullable = true) Headquarters headquarters,
    Registrant registrant,
    @Schema(nullable = true) CompanyContacts contacts,
    @Schema(nullable = true) List<String> selectedOS,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Set<String> knownFields) {
  public CompanyEvidence {
    selectedOS = selectedOS == null ? null : List.copyOf(selectedOS);
    knownFields = knownFields == null ? Set.of() : Set.copyOf(knownFields);
  }

  @Schema(name = "OnboardingOrganisationEvidence")
  public record Organisation(
      String organizationName,
      @Schema(nullable = true) String taxId,
      OrganizationType organizationType,
      String legalName,
      String registrationNumber,
      String industry,
      String website,
      String description) {}

  @Schema(name = "OnboardingHeadquartersEvidence")
  public record Headquarters(
      String addressLine1,
      String addressLine2,
      String city,
      String state,
      String district,
      String postalCode,
      String country,
      String addressLabel) {}

  @Schema(name = "OnboardingRegistrantEvidence")
  public record Registrant(
      String firstName,
      String lastName,
      String workEmail,
      @Schema(nullable = true) String mobilePhone) {}

  @Schema(name = "OnboardingCompanyContactEvidence")
  public record CompanyContacts(
      @Schema(nullable = true) String companyEmail, @Schema(nullable = true) String companyPhone) {}
}
