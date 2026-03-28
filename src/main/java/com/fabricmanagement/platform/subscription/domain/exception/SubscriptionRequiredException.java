package com.fabricmanagement.platform.subscription.domain.exception;

import com.fabricmanagement.common.infrastructure.web.exception.DomainException;

/**
 * Exception thrown when an operation requires an OS subscription that the tenant doesn't have.
 *
 * <p>This is thrown by Policy Engine Layer 1 when checking OS subscription access.
 *
 * <h2>HTTP Response — 402 Payment Required</h2>
 */
public class SubscriptionRequiredException extends DomainException {

  private final String requiredOs;

  public SubscriptionRequiredException(String message) {
    super(message, "SUBSCRIPTION_REQUIRED", 402);
    this.requiredOs = null;
  }

  public SubscriptionRequiredException(String message, String requiredOs) {
    super(message, "SUBSCRIPTION_REQUIRED", 402);
    this.requiredOs = requiredOs;
    if (requiredOs != null) {
      withDetail("requiredOs", requiredOs);
      withDetail("upgradeUrl", "/subscriptions/add/" + requiredOs);
    }
  }

  public String getRequiredOs() {
    return requiredOs;
  }
}
