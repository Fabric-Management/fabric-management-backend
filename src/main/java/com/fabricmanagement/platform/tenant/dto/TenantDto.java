package com.fabricmanagement.platform.tenant.dto;

import com.fabricmanagement.platform.tenant.domain.Tenant;
import com.fabricmanagement.platform.tenant.domain.TenantSettings;
import com.fabricmanagement.platform.tenant.domain.TenantStatus;
import com.fabricmanagement.platform.tenant.domain.TenantType;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Tenant entity.
 *
 * <p>Used in API responses and cross-module communication.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantDto {

  private UUID id;
  private String uid;
  private String slug;
  private String name;
  private String billingEmail;
  private TenantType type;
  private TenantStatus status;
  private Instant trialEndsAt;
  private Instant trialStartedAt;
  private Instant lastActivityAt;
  private boolean demoMode;

  /** Whether this tenant's outbound email is redirected to {@link #billingEmail}. */
  private boolean emailSandboxed;

  private String subscriptionPlan;
  private TenantSettings settings;
  private Instant createdAt;
  private Boolean isActive;

  /**
   * Create DTO from entity.
   *
   * @param tenant Tenant entity
   * @return DTO
   */
  public static TenantDto from(Tenant tenant) {
    if (tenant == null) {
      return null;
    }
    return TenantDto.builder()
        .id(tenant.getId())
        .uid(tenant.getUid())
        .slug(tenant.getSlug())
        .name(tenant.getName())
        .billingEmail(tenant.getBillingEmail())
        .type(tenant.getType())
        .status(tenant.getStatus())
        .trialEndsAt(tenant.getTrialEndsAt())
        .trialStartedAt(tenant.getTrialStartedAt())
        .lastActivityAt(tenant.getLastActivityAt())
        .demoMode(tenant.isDemoMode())
        .emailSandboxed(tenant.isEmailSandboxed())
        .subscriptionPlan(tenant.getSubscriptionPlan())
        .settings(tenant.getSettings())
        .createdAt(tenant.getCreatedAt())
        .isActive(tenant.getIsActive())
        .build();
  }
}
