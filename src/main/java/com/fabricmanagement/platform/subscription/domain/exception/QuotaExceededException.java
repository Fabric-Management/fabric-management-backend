package com.fabricmanagement.platform.subscription.domain.exception;

import com.fabricmanagement.common.infrastructure.web.exception.DomainException;

/**
 * Exception thrown when a usage quota has been exceeded.
 *
 * <p>This is thrown by Policy Engine Layer 3 when checking usage quotas.
 *
 * <h2>HTTP Response — 429 Too Many Requests</h2>
 */
public class QuotaExceededException extends DomainException {

  private final String quotaType;
  private final Long limit;
  private final Long used;

  public QuotaExceededException(String message) {
    super(message, "QUOTA_EXCEEDED", 429);
    this.quotaType = null;
    this.limit = null;
    this.used = null;
  }

  public QuotaExceededException(String message, String quotaType, Long limit, Long used) {
    super(message, "QUOTA_EXCEEDED", 429);
    this.quotaType = quotaType;
    this.limit = limit;
    this.used = used;
    if (quotaType != null) {
      withDetail("quotaType", quotaType);
    }
    if (limit != null) {
      withDetail("limit", limit);
    }
    if (used != null) {
      withDetail("used", used);
    }
  }

  public String getQuotaType() {
    return quotaType;
  }

  public Long getLimit() {
    return limit;
  }

  public Long getUsed() {
    return used;
  }
}
