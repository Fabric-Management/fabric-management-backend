package com.fabricmanagement.platform.user.domain;

import static org.assertj.core.api.Assertions.*;

import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OnboardingJourneyTest {
  @Test
  void sixAcceptedStepsAreRequiredBeforeFinalCompletion() {
    var journey =
        OnboardingJourney.company(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "PLAYGROUND");
    assertThat(journey.steps()).containsExactly(OnboardingStepId.values());
    assertThatThrownBy(() -> journey.accept(OnboardingStepId.YOUR_DETAILS, UUID.randomUUID(), true))
        .isInstanceOf(PlatformDomainException.class);
    assertThat(journey.getAcceptedSteps()).isEmpty();
    assertThatThrownBy(() -> journey.finish(UUID.randomUUID()))
        .isInstanceOf(PlatformDomainException.class);
    UUID review = UUID.randomUUID();
    for (var step : OnboardingStepId.values()) {
      journey.accept(step, step == OnboardingStepId.REVIEW ? review : UUID.randomUUID(), true);
      assertThat(journey.getCompletedAt()).isNull();
    }
    assertThat(journey.getCommandVersion()).isEqualTo(6);
    assertThatThrownBy(() -> journey.finish(UUID.randomUUID()))
        .isInstanceOf(PlatformDomainException.class);
    journey.finish(review);
    assertThat(journey.getCompletedAt()).isNotNull();
    assertThat(journey.getCommandVersion()).isEqualTo(7);
  }

  @Test
  void changedEarlierStepRequiresExactlyReviewReconfirmation() {
    var journey =
        OnboardingJourney.company(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "TRIAL");
    for (var step : journey.steps()) journey.accept(step, UUID.randomUUID(), true);
    assertThat(journey.accept(OnboardingStepId.ADDRESS, UUID.randomUUID(), false)).isEmpty();
    assertThat(journey.getReviewOperationId()).isNotNull();
    assertThat(journey.accept(OnboardingStepId.ADDRESS, UUID.randomUUID(), true))
        .containsExactly(OnboardingStepId.REVIEW);
    assertThat(journey.firstIncomplete()).isEqualTo(OnboardingStepId.REVIEW);
    assertThat(journey.getReviewOperationId()).isNull();
    assertThat(journey.getAcceptedSteps())
        .containsKeys(OnboardingStepId.ORGANIZATION_DETAILS, OnboardingStepId.PLATFORM_MODULES);
  }

  @Test
  void legacyCorrectionInvalidatesPendingReviewAndPreservesFirstCompletion() {
    var journey =
        OnboardingJourney.company(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "PLAYGROUND");
    for (var step : journey.steps()) journey.accept(step, UUID.randomUUID(), true);
    journey.finish(journey.getReviewOperationId());
    var firstCompletion = journey.getCompletedAt();
    journey.accept(OnboardingStepId.ADDRESS, UUID.randomUUID(), true);
    UUID pendingReview = UUID.randomUUID();
    journey.accept(OnboardingStepId.REVIEW, pendingReview, false);
    long before = journey.getCommandVersion();

    journey.retainedLegacyCorrection();

    assertThat(journey.getCommandVersion()).isEqualTo(before + 1);
    assertThat(journey.getAcceptedSteps()).doesNotContainKey(OnboardingStepId.REVIEW);
    assertThat(journey.getReviewOperationId()).isNull();
    assertThat(journey.firstIncomplete()).isEqualTo(OnboardingStepId.REVIEW);
    assertThat(journey.getCompletedAt()).isEqualTo(firstCompletion);
    assertThatThrownBy(() -> journey.finish(pendingReview))
        .isInstanceOfSatisfying(
            PlatformDomainException.class,
            failure -> assertThat(failure.getErrorCode()).isEqualTo("ONBOARDING_REVIEW_REQUIRED"));
    UUID renewedReview = UUID.randomUUID();
    journey.accept(OnboardingStepId.REVIEW, renewedReview, false);
    journey.finish(renewedReview);
    assertThat(journey.getFinalizedReviewOperationId()).isEqualTo(renewedReview);
    assertThat(journey.getCompletedAt()).isEqualTo(firstCompletion);
  }

  @Test
  void invitedPersonalJourneyNeverIncludesCompanySteps() {
    var journey = OnboardingJourney.personal(UUID.randomUUID(), UUID.randomUUID());
    assertThat(journey.steps()).isEqualTo(List.of(OnboardingStepId.YOUR_DETAILS));
    assertThatThrownBy(() -> journey.requireOrder(OnboardingStepId.ORGANIZATION_DETAILS))
        .isInstanceOf(PlatformDomainException.class);
    journey.accept(OnboardingStepId.YOUR_DETAILS, UUID.randomUUID(), true);
    journey.finish(null);
    assertThat(journey.getCompletedAt()).isNotNull();
  }

  @Test
  void legacyPersonalCompletionDoesNotInventAcceptedStepEvidence() {
    var journey = OnboardingJourney.personal(UUID.randomUUID(), UUID.randomUUID());
    var completed = java.time.Instant.parse("2026-10-24T10:00:00Z");
    journey.completePersonalLegacy(completed);
    assertThat(journey.getCompletedAt()).isEqualTo(completed);
    assertThat(journey.getAcceptedSteps()).isEmpty();
    assertThat(journey.isLegacyCompleted()).isTrue();
    assertThat(journey.firstIncomplete()).isNull();
  }
}
