package com.fabricmanagement.common.platform.company.dto;

import com.fabricmanagement.common.platform.company.domain.SubscriptionQuota;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for subscription quota. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionQuotaDto {

  private UUID id;
  private UUID tenantId;
  private UUID subscriptionId;
  private String quotaType;
  private Long quotaLimit;
  private Long quotaUsed;
  private Long remaining;
  private Double usagePercentage;
  private String resetPeriod;
  private Instant lastResetAt;
  private Instant createdAt;

  public static SubscriptionQuotaDto from(SubscriptionQuota quota) {
    if (quota == null) {
      return null;
    }

    return SubscriptionQuotaDto.builder()
        .id(quota.getId())
        .tenantId(quota.getTenantId())
        .subscriptionId(quota.getSubscriptionId())
        .quotaType(quota.getQuotaType())
        .quotaLimit(quota.getQuotaLimit())
        .quotaUsed(quota.getQuotaUsed())
        .remaining(quota.remaining())
        .usagePercentage(quota.usagePercentage())
        .resetPeriod(quota.getResetPeriod())
        .lastResetAt(quota.getLastResetAt())
        .createdAt(quota.getCreatedAt())
        .build();
  }
}
