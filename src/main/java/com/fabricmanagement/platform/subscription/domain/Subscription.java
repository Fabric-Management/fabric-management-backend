package com.fabricmanagement.platform.subscription.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.*;
import org.hibernate.annotations.Type;

/**
 * OS Subscription entity.
 *
 * <p>Represents a tenant's subscription to an Operating Subscription (OS). This is the foundation
 * of Layer 1 in the 5-layer Policy Engine.
 *
 * <h2>Policy Engine Integration:</h2>
 *
 * <p>Before ANY operation, the policy engine checks if the tenant has an ACTIVE subscription to the
 * required OS. No subscription = Access DENIED.
 *
 * <h2>Lifecycle:</h2>
 *
 * <pre>
 * TRIAL (14-30 days) → ACTIVE (paid) → EXPIRED (end date)
 *    ↓                    ↓                ↓
 * EXPIRED            SUSPENDED         CANCELLED
 * </pre>
 *
 * <h2>Example:</h2>
 *
 * <pre>{@code
 * Subscription yarnOS = Subscription.builder()
 *     .osCode("YarnOS")
 *     .osName("Yarn Production OS")
 *     .status(SubscriptionStatus.TRIAL)
 *     .startDate(Instant.now())
 *     .trialEndsAt(Instant.now().plus(90, ChronoUnit.DAYS))
 *     .pricingTier("Professional")
 *     .build();
 * }</pre>
 *
 * <h2>Pricing Tier Examples by OS:</h2>
 *
 * <ul>
 *   <li>YarnOS: "Starter", "Professional", "Enterprise"
 *   <li>AnalyticsOS: "Standard", "Advanced", "Enterprise"
 *   <li>IntelligenceOS: "Professional", "Enterprise"
 *   <li>EdgeOS: "Starter", "Professional", "Enterprise"
 * </ul>
 */
@Entity
@Table(
    name = "common_subscription",
    schema = "common_company",
    indexes = {
      @Index(name = "idx_subscription_tenant_os", columnList = "tenant_id,os_code"),
      @Index(name = "idx_subscription_status", columnList = "status")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subscription extends BaseEntity {

  @Column(name = "os_code", nullable = false, length = 50)
  private String osCode;

  @Column(name = "os_name", nullable = false, length = 255)
  private String osName;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  @Builder.Default
  private SubscriptionStatus status = SubscriptionStatus.TRIAL;

  @Column(name = "start_date", nullable = false)
  private Instant startDate;

  @Column(name = "expiry_date")
  private Instant expiryDate;

  @Column(name = "trial_ends_at")
  private Instant trialEndsAt;

  @Type(JsonType.class)
  @Column(name = "features", columnDefinition = "jsonb")
  @Builder.Default
  private Map<String, Boolean> features = new HashMap<>();

  /**
   * Pricing tier (OPTIONAL - for future feature-gating).
   *
   * <p><b>Simple Model:</b> OS-based pricing only (no tiers)
   *
   * <p>This field is nullable for simple subscription model.
   */
  @Column(name = "pricing_tier", length = 50)
  private String pricingTier;

  @Column(name = "evaluation_managed", nullable = false)
  private boolean evaluationManaged;

  @Column(name = "learning_access", nullable = false)
  private boolean learningAccess;

  @Column(name = "evaluation_selection_source", length = 30)
  private String evaluationSelectionSource;

  /** Evaluation selection never calls activate(), which means paid access. */
  public void selectEvaluation(boolean learning, Instant deadline, String source) {
    if (status == SubscriptionStatus.ACTIVE
        || status == SubscriptionStatus.CANCELLED
        || (status == SubscriptionStatus.SUSPENDED && !evaluationManaged)) {
      throw new com.fabricmanagement.platform.common.exception.PlatformDomainException(
          "Existing subscription cannot be changed through evaluation onboarding",
          "ONBOARDING_SUBSCRIPTION_CONFLICT",
          409);
    }
    if (!learning && (deadline == null || !deadline.isAfter(Instant.now()))) {
      throw new com.fabricmanagement.platform.common.exception.PlatformDomainException(
          "An active real trial window is required", "ONBOARDING_EVALUATION_UNAVAILABLE", 409);
    }
    evaluationManaged = true;
    learningAccess = learning;
    evaluationSelectionSource = source;
    status = SubscriptionStatus.TRIAL;
    trialEndsAt = learning ? null : deadline;
    expiryDate = null;
  }

  public void deselectEvaluation() {
    if (!evaluationManaged || status != SubscriptionStatus.TRIAL) return;
    status = SubscriptionStatus.SUSPENDED;
    learningAccess = false;
    evaluationSelectionSource = "OWNER";
  }

  public boolean isActive() {
    return isActiveAt(Instant.now());
  }

  public boolean isActiveAt(Instant now) {
    if (!Boolean.TRUE.equals(getIsActive()) || getDeletedAt() != null) return false;
    if (this.status == SubscriptionStatus.ACTIVE) {
      if (this.expiryDate == null) {
        return true;
      }
      return this.expiryDate.isAfter(now);
    }

    if (this.status == SubscriptionStatus.TRIAL) {
      return learningAccess || (this.trialEndsAt != null && this.trialEndsAt.isAfter(now));
    }

    return false;
  }

  public boolean isExpired() {
    return this.status == SubscriptionStatus.EXPIRED
        || (this.expiryDate != null && this.expiryDate.isBefore(Instant.now()))
        || (!learningAccess
            && this.status == SubscriptionStatus.TRIAL
            && this.trialEndsAt != null
            && this.trialEndsAt.isBefore(Instant.now()));
  }

  public void activate() {
    this.status = SubscriptionStatus.ACTIVE;
    this.learningAccess = false;
    this.evaluationManaged = false;
    this.trialEndsAt = null;
  }

  public void expire() {
    this.learningAccess = false;
    this.status = SubscriptionStatus.EXPIRED;
  }

  public void cancel() {
    this.learningAccess = false;
    this.status = SubscriptionStatus.CANCELLED;
  }

  public void suspend() {
    this.learningAccess = false;
    this.status = SubscriptionStatus.SUSPENDED;
  }

  public void resume() {
    if (this.status == SubscriptionStatus.SUSPENDED) {
      if (evaluationManaged) {
        throw new com.fabricmanagement.platform.common.exception.PlatformDomainException(
            "Use the versioned evaluation modules step", "ONBOARDING_MODULES_STEP_REQUIRED", 409);
      }
      this.status = SubscriptionStatus.ACTIVE;
    }
  }

  public boolean hasFeature(String featureName) {
    return features.getOrDefault(featureName, false);
  }

  public void enableFeature(String featureName) {
    this.features.put(featureName, true);
  }

  public void disableFeature(String featureName) {
    this.features.put(featureName, false);
  }

  @Override
  protected String getModuleCode() {
    return "SUB";
  }

  /**
   * Validate subscription before persisting.
   *
   * <p>Simple model: Only OS-based pricing (no tiers)
   */
  @PrePersist
  @PreUpdate
  private void validate() {
    // Simple validation: osCode required
    if (osCode == null || osCode.isBlank()) {
      throw new IllegalStateException("OS code is required");
    }

    // pricingTier is optional (simple OS-based model)
    // No tier validation needed
  }
}
