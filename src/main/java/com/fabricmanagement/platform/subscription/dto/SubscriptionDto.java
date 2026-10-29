package com.fabricmanagement.platform.subscription.dto;

import com.fabricmanagement.platform.subscription.domain.Subscription;
import com.fabricmanagement.platform.subscription.domain.SubscriptionStatus;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionDto {

  private UUID id;
  private UUID tenantId;
  private String uid;
  private String osCode;
  private String osName;
  private SubscriptionStatus status;
  private Instant startDate;
  private Instant expiryDate;
  private Instant trialEndsAt;

  @io.swagger.v3.oas.annotations.media.Schema(
      description = "Non-paid evaluation access managed by company onboarding")
  private boolean evaluationManaged;

  @io.swagger.v3.oas.annotations.media.Schema(
      description = "Learning access has no package trial deadline; go-real starts it")
  private boolean learningAccess;

  @io.swagger.v3.oas.annotations.media.Schema(
      nullable = true,
      description =
          "SERVER_DEFAULT, OWNER, REPAIRED_DEFAULT or REPAIRED_PRESERVED; not payment consent")
  private String evaluationSelectionSource;

  private Map<String, Boolean> features;

  /**
   * Pricing tier for this subscription.
   *
   * <p>Tier names vary by OS:
   *
   * <ul>
   *   <li>YarnOS, LoomOS, etc.: "Starter", "Professional", "Enterprise"
   *   <li>AnalyticsOS: "Standard", "Advanced", "Enterprise"
   *   <li>IntelligenceOS: "Professional", "Enterprise"
   * </ul>
   */
  private String pricingTier;

  private Boolean isActive;
  private Instant createdAt;

  public static SubscriptionDto from(Subscription subscription) {
    return SubscriptionDto.builder()
        .id(subscription.getId())
        .tenantId(subscription.getTenantId())
        .uid(subscription.getUid())
        .osCode(subscription.getOsCode())
        .osName(subscription.getOsName())
        .status(subscription.getStatus())
        .startDate(subscription.getStartDate())
        .expiryDate(subscription.getExpiryDate())
        .trialEndsAt(subscription.getTrialEndsAt())
        .evaluationManaged(subscription.isEvaluationManaged())
        .learningAccess(subscription.isLearningAccess())
        .evaluationSelectionSource(subscription.getEvaluationSelectionSource())
        .features(subscription.getFeatures())
        .pricingTier(subscription.getPricingTier())
        .isActive(subscription.isActive())
        .createdAt(subscription.getCreatedAt())
        .build();
  }
}
