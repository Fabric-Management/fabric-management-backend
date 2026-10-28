package com.fabricmanagement.platform.user.dto;

import com.fabricmanagement.platform.lead.domain.CompanyEvidence;
import com.fabricmanagement.platform.user.domain.OnboardingJourney.Kind;
import com.fabricmanagement.platform.user.domain.OnboardingStepId;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(
    description =
        "Durable accepted evidence, never unsubmitted browser drafts. Company values"
            + " require fresh company-setup authority. Registrant mobile is visible only to that registrant."
            + " completedAt is the historical first completion; legacy completion does not invent step evidence.")
@com.fasterxml.jackson.annotation.JsonInclude(
    com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
public record OnboardingJourneyResponse(
    UUID tenantId,
    UUID userId,
    @Schema(nullable = true) UUID journeyId,
    Kind kind,
    @Schema(nullable = true) String signupIntent,
    long version,
    List<StepStatus> steps,
    @Schema(nullable = true) OnboardingStepId firstIncompleteStep,
    @Schema(nullable = true) CompanyEvidence acceptedValues,
    @Schema(nullable = true) String personalMobilePhone,
    @Schema(nullable = true) Instant completedAt,
    boolean legacyCompleted,
    List<OnboardingStepId> requiresReconfirmation,
    OnboardingCompanyCapability companySetup,
    ActionCapability save,
    ActionCapability complete,
    @Schema(nullable = true)
        com.fabricmanagement.platform.subscription.dto.EvaluationPackageSelection packages,
    ActionCapability selectModules) {
  @Schema(name = "OnboardingJourneyStepStatus")
  @com.fasterxml.jackson.annotation.JsonInclude(
      com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
  public record StepStatus(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OnboardingStepId stepId,
      @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) Instant acceptedAt,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean completed,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean allowed,
      @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) String reason) {}

  @Schema(name = "OnboardingJourneyActionCapability")
  @com.fasterxml.jackson.annotation.JsonInclude(
      com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
  public record ActionCapability(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Action action,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean allowed,
      @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) String reason,
      @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) String routesTo,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean needsApproval) {}

  @Schema(name = "OnboardingJourneyAction", enumAsRef = true)
  public enum Action {
    SAVE_STEP,
    COMPLETE_JOURNEY,
    SELECT_MODULES
  }
}
