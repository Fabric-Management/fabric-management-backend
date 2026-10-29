package com.fabricmanagement.platform.user.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Accepted progress is independent of demo mode and billing state. */
@Entity
@Table(name = "onboarding_journey", schema = "common_user")
@Getter
@NoArgsConstructor
public class OnboardingJourney extends BaseEntity {
  @io.swagger.v3.oas.annotations.media.Schema(name = "OnboardingJourneyKind", enumAsRef = true)
  public enum Kind {
    COMPANY,
    PERSONAL
  }

  @Column(name = "owner_user_id", nullable = false, updatable = false)
  private UUID ownerUserId;

  @Column(name = "signup_id", updatable = false)
  private UUID signupId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false)
  private Kind kind;

  @Column(name = "signup_intent", updatable = false)
  private String signupIntent;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "accepted_steps", nullable = false, columnDefinition = "jsonb")
  private Map<OnboardingStepId, Instant> acceptedSteps = new EnumMap<>(OnboardingStepId.class);

  @Column(name = "review_operation_id")
  private UUID reviewOperationId;

  @Column(name = "finalized_review_operation_id")
  private UUID finalizedReviewOperationId;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "last_accepted_at")
  private Instant lastAcceptedAt;

  @Column(name = "personal_mobile")
  private String personalMobile;

  @Column(name = "legacy_completed", nullable = false)
  private boolean legacyCompleted;

  @Column(name = "command_version", nullable = false)
  private long commandVersion;

  public static OnboardingJourney company(UUID tenant, UUID owner, UUID signup, String intent) {
    var journey = new OnboardingJourney();
    journey.setTenantId(tenant);
    journey.ownerUserId = owner;
    journey.signupId = signup;
    journey.signupIntent = intent;
    journey.kind = Kind.COMPANY;
    return journey;
  }

  public static OnboardingJourney personal(UUID tenant, UUID owner) {
    var journey = new OnboardingJourney();
    journey.setTenantId(tenant);
    journey.ownerUserId = owner;
    journey.kind = Kind.PERSONAL;
    return journey;
  }

  public List<OnboardingStepId> steps() {
    return kind == Kind.COMPANY
        ? List.of(OnboardingStepId.values())
        : List.of(OnboardingStepId.YOUR_DETAILS);
  }

  public OnboardingStepId firstIncomplete() {
    if (legacyCompleted) return null;
    return steps().stream()
        .filter(step -> !acceptedSteps.containsKey(step))
        .findFirst()
        .orElse(null);
  }

  public void requireOrder(OnboardingStepId step) {
    if (!steps().contains(step))
      throw new PlatformDomainException(
          "Step does not belong to this journey", "ONBOARDING_STEP_INVALID", 422);
    if (!isInOrder(step))
      throw new PlatformDomainException(
              "Complete preceding onboarding steps first", "ONBOARDING_STEP_OUT_OF_ORDER", 409)
          .withDetail("firstIncompleteStep", firstIncomplete());
  }

  public boolean isInOrder(OnboardingStepId step) {
    return steps().contains(step)
        && steps().subList(0, steps().indexOf(step)).stream().allMatch(acceptedSteps::containsKey);
  }

  public List<OnboardingStepId> accept(OnboardingStepId step, UUID operationId, boolean changed) {
    requireOrder(step);
    List<OnboardingStepId> invalidated = new ArrayList<>();
    var next = new EnumMap<OnboardingStepId, Instant>(OnboardingStepId.class);
    next.putAll(acceptedSteps);
    if (step != OnboardingStepId.REVIEW
        && changed
        && next.remove(OnboardingStepId.REVIEW) != null) {
      invalidated.add(OnboardingStepId.REVIEW);
      reviewOperationId = null;
    }
    lastAcceptedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    commandVersion++;
    next.put(step, lastAcceptedAt);
    acceptedSteps = next;
    if (step == OnboardingStepId.REVIEW) reviewOperationId = operationId;
    return List.copyOf(invalidated);
  }

  public void finish(UUID reviewId) {
    if (steps().stream().anyMatch(step -> !acceptedSteps.containsKey(step))
        || (kind == Kind.COMPANY
            && (reviewId == null || !Objects.equals(reviewId, reviewOperationId)))) {
      throw new PlatformDomainException(
              "Review all accepted steps before completion", "ONBOARDING_REVIEW_REQUIRED", 409)
          .withDetail("firstIncompleteStep", firstIncomplete());
    }
    if (!needsCompletion()) return;
    if (completedAt == null)
      completedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    finalizedReviewOperationId = reviewOperationId;
    lastAcceptedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    commandVersion++;
  }

  public boolean needsCompletion() {
    if (legacyCompleted || firstIncomplete() != null) return false;
    return kind == Kind.PERSONAL
        ? completedAt == null
        : reviewOperationId != null
            && !Objects.equals(reviewOperationId, finalizedReviewOperationId);
  }

  public void retainedLegacyCorrection() {
    // Legacy patches change the same accepted evidence as step commands. A review of the
    // previous snapshot cannot authorise completion of the corrected snapshot.
    var next = new EnumMap<OnboardingStepId, Instant>(OnboardingStepId.class);
    next.putAll(acceptedSteps);
    next.remove(OnboardingStepId.REVIEW);
    acceptedSteps = next;
    reviewOperationId = null;
    lastAcceptedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    commandVersion++;
  }

  public void acceptPersonalMobile(String mobile) {
    personalMobile = mobile;
  }

  public void preservePersonalLegacyCompletion(Instant completion) {
    if (kind == Kind.PERSONAL
        && completedAt == null
        && completion != null
        && acceptedSteps.isEmpty()) {
      completedAt = completion;
      legacyCompleted = true;
    }
  }

  public void completePersonalLegacy(Instant completion) {
    if (kind == Kind.PERSONAL && completedAt == null && completion != null) {
      completedAt = completion.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
      legacyCompleted = true;
      commandVersion++;
    }
  }

  @Override
  protected String getModuleCode() {
    return "ONB";
  }
}
