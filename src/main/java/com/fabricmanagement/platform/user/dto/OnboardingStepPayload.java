package com.fabricmanagement.platform.user.dto;

import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.platform.user.domain.OnboardingStepId;
import com.fasterxml.jackson.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.util.List;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "stepId",
    visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(
      value = OnboardingStepPayload.OrganizationDetails.class,
      name = "ORGANIZATION_DETAILS"),
  @JsonSubTypes.Type(value = OnboardingStepPayload.Address.class, name = "ADDRESS"),
  @JsonSubTypes.Type(value = OnboardingStepPayload.YourDetails.class, name = "YOUR_DETAILS"),
  @JsonSubTypes.Type(
      value = OnboardingStepPayload.OrganizationContacts.class,
      name = "ORGANIZATION_CONTACTS"),
  @JsonSubTypes.Type(
      value = OnboardingStepPayload.PlatformModules.class,
      name = "PLATFORM_MODULES"),
  @JsonSubTypes.Type(value = OnboardingStepPayload.Review.class, name = "REVIEW")
})
@Schema(
    name = "OnboardingStepPayload",
    description =
        "Exactly one typed step. The discriminator must match the route stepId."
            + " Unknown/cross-step fields are rejected. Absent/null optional fields preserve prior values;"
            + " empty optional profile/address scalars explicitly clear them. Contacts cannot be cleared."
            + " Only submitted, validated steps are accepted; browser keystrokes remain drafts.")
public sealed interface OnboardingStepPayload {
  @JsonIgnore
  OnboardingStepId id();

  @JsonAnySetter
  default void rejectUnknown(String field, Object ignored) {
    throw new IllegalArgumentException("Unknown field for onboarding step");
  }

  private static void requireStep(String value, OnboardingStepId expected) {
    if (!expected.name().equals(value))
      throw new IllegalArgumentException("Wrong onboarding step discriminator");
  }

  @Schema(
      name = "OnboardingOrganizationStep",
      additionalProperties = Schema.AdditionalPropertiesValue.FALSE,
      description = "Internal organisation details; EXTERNAL_PARTNER is not a tenant root type.")
  record OrganizationDetails(
      @NotBlank
          @Schema(
              allowableValues = "ORGANIZATION_DETAILS",
              requiredMode = Schema.RequiredMode.REQUIRED)
          String stepId,
      @NotBlank @Size(min = 2, max = 255) String organizationName,
      @NotBlank @Size(min = 5, max = 50) String taxId,
      @NotNull OrganizationType organizationType,
      @Size(max = 200) String legalName,
      @Size(max = 100) String registrationNumber,
      @NotBlank @Size(max = 100) String industry,
      @Size(max = 500)
          @Pattern(
              regexp = "^(https?:\\/\\/)?([a-zA-Z0-9.-]+)\\.([a-zA-Z]{2,6})[\\/\\w .-]*\\/?$|^$")
          String website,
      @Size(max = 2000) String description)
      implements OnboardingStepPayload {
    public OrganizationDetails {
      requireStep(stepId, OnboardingStepId.ORGANIZATION_DETAILS);
    }

    public OrganizationDetails(
        String name,
        String tax,
        OrganizationType type,
        String legal,
        String registration,
        String industry,
        String website,
        String description) {
      this(
          "ORGANIZATION_DETAILS",
          name,
          tax,
          type,
          legal,
          registration,
          industry,
          website,
          description);
    }

    @Override
    public OnboardingStepId id() {
      return OnboardingStepId.ORGANIZATION_DETAILS;
    }
  }

  @Schema(
      name = "OnboardingAddressStep",
      additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
  record Address(
      @NotBlank @Schema(allowableValues = "ADDRESS", requiredMode = Schema.RequiredMode.REQUIRED)
          String stepId,
      @NotBlank @Size(max = 255) String addressLine1,
      @Size(max = 255) String addressLine2,
      @NotBlank @Size(max = 100) String city,
      @Size(max = 100) String state,
      @Size(max = 100) String district,
      @Size(max = 20) String postalCode,
      @NotBlank @Size(max = 100) String country,
      @Size(min = 1, max = 100) @Pattern(regexp = ".*\\S.*") String addressLabel)
      implements OnboardingStepPayload {
    public Address {
      requireStep(stepId, OnboardingStepId.ADDRESS);
    }

    public Address(
        String street,
        String line2,
        String city,
        String state,
        String district,
        String postcode,
        String country,
        String label) {
      this("ADDRESS", street, line2, city, state, district, postcode, country, label);
    }

    @Override
    public OnboardingStepId id() {
      return OnboardingStepId.ADDRESS;
    }
  }

  @Schema(
      name = "OnboardingPersonalStep",
      additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
  record YourDetails(
      @NotBlank
          @Schema(allowableValues = "YOUR_DETAILS", requiredMode = Schema.RequiredMode.REQUIRED)
          String stepId,
      @NotBlank @Size(max = 20) @Pattern(regexp = "^\\+[1-9]\\d{7,14}$") String mobilePhone)
      implements OnboardingStepPayload {
    public YourDetails {
      requireStep(stepId, OnboardingStepId.YOUR_DETAILS);
    }

    public YourDetails(String mobile) {
      this("YOUR_DETAILS", mobile);
    }

    @Override
    public OnboardingStepId id() {
      return OnboardingStepId.YOUR_DETAILS;
    }
  }

  @Schema(
      name = "OnboardingContactStep",
      additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
  record OrganizationContacts(
      @NotBlank
          @Schema(
              allowableValues = "ORGANIZATION_CONTACTS",
              requiredMode = Schema.RequiredMode.REQUIRED)
          String stepId,
      @Email @Size(min = 1, max = 254) String companyEmail,
      @Size(min = 1, max = 20) @Pattern(regexp = ".*\\S.*") String companyPhone)
      implements OnboardingStepPayload {
    public OrganizationContacts {
      requireStep(stepId, OnboardingStepId.ORGANIZATION_CONTACTS);
    }

    public OrganizationContacts(String email, String phone) {
      this("ORGANIZATION_CONTACTS", email, phone);
    }

    @Override
    public OnboardingStepId id() {
      return OnboardingStepId.ORGANIZATION_CONTACTS;
    }
  }

  @Schema(
      name = "OnboardingModulesStep",
      additionalProperties = Schema.AdditionalPropertiesValue.FALSE,
      description =
          "Atomically saves evaluation access and accepted evidence without billing consent. FabricOS is required."
              + " The registrant must send the current catalogueVersion; reload on a catalogue conflict."
              + " Paid tenants and PLAYGROUND tenants after go-real cannot change access through this step.")
  record PlatformModules(
      @NotBlank
          @Schema(allowableValues = "PLATFORM_MODULES", requiredMode = Schema.RequiredMode.REQUIRED)
          String stepId,
      @NotEmpty @Size(max = 100) List<@NotBlank @Size(max = 100) String> selectedOS,
      @NotBlank
          @Size(max = 64)
          @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description = "Catalogue version returned with the journey; reload on 409")
          String catalogueVersion)
      implements OnboardingStepPayload {
    public PlatformModules {
      requireStep(stepId, OnboardingStepId.PLATFORM_MODULES);
      selectedOS = selectedOS == null ? null : List.copyOf(selectedOS);
    }

    public PlatformModules(List<String> codes) {
      this("PLATFORM_MODULES", codes, null);
    }

    public PlatformModules(List<String> codes, String version) {
      this("PLATFORM_MODULES", codes, version);
    }

    @Override
    public OnboardingStepId id() {
      return OnboardingStepId.PLATFORM_MODULES;
    }

    @JsonIgnore
    @AssertTrue(message = "FabricOS must be selected")
    public boolean isFabricOSSelected() {
      return selectedOS != null && selectedOS.contains("FabricOS");
    }
  }

  @Schema(
      name = "OnboardingReviewStep",
      additionalProperties = Schema.AdditionalPropertiesValue.FALSE,
      description =
          "Confirms exactly the accepted journey version shown to the owner."
              + " Acceptance does not complete onboarding; use the versioned completion command.")
  record Review(
      @NotBlank @Schema(allowableValues = "REVIEW", requiredMode = Schema.RequiredMode.REQUIRED)
          String stepId,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description = "Explicit confirmation; must be true")
          @AssertTrue
          boolean confirmed,
      @NotNull @PositiveOrZero Long reviewedVersion)
      implements OnboardingStepPayload {
    public Review {
      requireStep(stepId, OnboardingStepId.REVIEW);
    }

    public Review(boolean confirmed, Long version) {
      this("REVIEW", confirmed, version);
    }

    @Override
    public OnboardingStepId id() {
      return OnboardingStepId.REVIEW;
    }
  }
}
