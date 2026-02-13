package com.fabricmanagement.common.platform.subscription.dto;

import java.time.Instant;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for updating a subscription. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSubscriptionRequest {

  /** New expiry date (optional). */
  private Instant expiryDate;

  /** Updated features map (optional). */
  private Map<String, Boolean> features;

  /** New pricing tier (optional). */
  private String pricingTier;
}
