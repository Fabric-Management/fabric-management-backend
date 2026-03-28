package com.fabricmanagement.platform.subscription.domain.exception;

import com.fabricmanagement.common.infrastructure.web.exception.DomainException;

/**
 * Exception thrown when a feature is not available in the tenant's subscription tier.
 *
 * <p>This is thrown by Policy Engine Layer 2 when checking feature entitlements.
 *
 * <h2>HTTP Response — 402 Payment Required</h2>
 */
public class FeatureNotAvailableException extends DomainException {

  private final String featureId;
  private final String minimumTier;

  public FeatureNotAvailableException(String message) {
    super(message, "FEATURE_NOT_AVAILABLE", 402);
    this.featureId = null;
    this.minimumTier = null;
  }

  public FeatureNotAvailableException(String message, String featureId, String minimumTier) {
    super(message, "FEATURE_NOT_AVAILABLE", 402);
    this.featureId = featureId;
    this.minimumTier = minimumTier;
    if (featureId != null) {
      withDetail("featureId", featureId);
    }
    if (minimumTier != null) {
      withDetail("minimumTier", minimumTier);
    }
  }

  public String getFeatureId() {
    return featureId;
  }

  public String getMinimumTier() {
    return minimumTier;
  }
}
