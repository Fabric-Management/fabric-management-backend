package com.fabricmanagement.platform.user.app;

import com.fabricmanagement.platform.lead.domain.CompanyEvidence;
import com.fabricmanagement.platform.user.domain.*;
import com.fabricmanagement.platform.user.dto.*;
import com.fabricmanagement.platform.user.dto.OnboardingJourneyResponse.*;
import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OnboardingJourneyViewMapper {
  public OnboardingJourneyResponse view(
      OnboardingJourney journey,
      User actor,
      OnboardingCompanyCapability company,
      CompanyEvidence evidence,
      List<OnboardingStepId> reconfirm) {
    return view(journey, actor, company, evidence, reconfirm, null);
  }

  public OnboardingJourneyResponse view(
      OnboardingJourney journey,
      User actor,
      OnboardingCompanyCapability company,
      CompanyEvidence evidence,
      List<OnboardingStepId> reconfirm,
      com.fabricmanagement.platform.subscription.dto.EvaluationPackageSelection packages) {
    boolean owns = journey.getOwnerUserId().equals(actor.getId());
    boolean allowed = journey.getKind() == OnboardingJourney.Kind.PERSONAL || company.allowed();
    boolean modulesAllowed = company.allowed() && owns && packages != null && packages.editable();
    var visiblePackages =
        !allowed || packages == null
            ? null
            : owns
                ? packages
                : new com.fabricmanagement.platform.subscription.dto.EvaluationPackageSelection(
                    packages.catalogueVersion(),
                    packages.options(),
                    packages.selectedCodes(),
                    false,
                    "ONBOARDING_REGISTRANT_REQUIRED",
                    packages.source());
    if (!allowed) evidence = null;
    else if (!owns && evidence != null) {
      var registrant = evidence.registrant();
      var known = new HashSet<>(evidence.knownFields());
      known.remove("mobilePhone");
      evidence =
          new CompanyEvidence(
              evidence.organisation(),
              evidence.headquarters(),
              new CompanyEvidence.Registrant(
                  registrant.firstName(), registrant.lastName(), registrant.workEmail(), null),
              evidence.contacts(),
              evidence.selectedOS(),
              known);
    }
    var steps =
        journey.steps().stream()
            .map(
                step -> {
                  boolean personal =
                      step == OnboardingStepId.YOUR_DETAILS
                          || step == OnboardingStepId.REVIEW
                          || step == OnboardingStepId.PLATFORM_MODULES;
                  boolean module = step == OnboardingStepId.PLATFORM_MODULES;
                  boolean canEdit =
                      allowed
                          && (!personal || owns)
                          && journey.isInOrder(step)
                          && (!module || modulesAllowed);
                  return new StepStatus(
                      step,
                      journey.getAcceptedSteps().get(step),
                      journey.getAcceptedSteps().containsKey(step),
                      canEdit,
                      !allowed
                          ? company.reason()
                          : personal && !owns
                              ? "ONBOARDING_REGISTRANT_REQUIRED"
                              : module && !modulesAllowed
                                  ? "ONBOARDING_EVALUATION_UNAVAILABLE"
                                  : !journey.isInOrder(step)
                                      ? "ONBOARDING_STEP_OUT_OF_ORDER"
                                      : null);
                })
            .toList();
    boolean complete = allowed && owns && journey.needsCompletion();
    return new OnboardingJourneyResponse(
        actor.getTenantId(),
        actor.getId(),
        journey.getId(),
        journey.getKind(),
        journey.getSignupIntent(),
        journey.getCommandVersion(),
        steps,
        journey.firstIncomplete(),
        evidence,
        owns ? journey.getPersonalMobile() : null,
        journey.getCompletedAt() != null
            ? journey.getCompletedAt()
            : journey.getKind() == OnboardingJourney.Kind.PERSONAL
                ? actor.getOnboardingCompletedAt()
                : null,
        journey.isLegacyCompleted(),
        reconfirm,
        company,
        new ActionCapability(
            Action.SAVE_STEP, allowed, allowed ? null : company.reason(), null, false),
        new ActionCapability(
            Action.COMPLETE_JOURNEY,
            complete,
            complete
                ? null
                : !allowed
                    ? company.reason()
                    : !owns
                        ? "ONBOARDING_REGISTRANT_REQUIRED"
                        : journey.getCompletedAt() != null && journey.firstIncomplete() == null
                            ? "ONBOARDING_ALREADY_COMPLETED"
                            : "ONBOARDING_REVIEW_REQUIRED",
            null,
            false),
        visiblePackages,
        new ActionCapability(
            Action.SELECT_MODULES,
            modulesAllowed && journey.isInOrder(OnboardingStepId.PLATFORM_MODULES),
            !owns
                ? "ONBOARDING_REGISTRANT_REQUIRED"
                : !modulesAllowed
                    ? "ONBOARDING_EVALUATION_UNAVAILABLE"
                    : !journey.isInOrder(OnboardingStepId.PLATFORM_MODULES)
                        ? "ONBOARDING_STEP_OUT_OF_ORDER"
                        : null,
            null,
            false));
  }
}
