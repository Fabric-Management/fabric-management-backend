package com.fabricmanagement.platform.auth.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tenant onboarding response.
 *
 * <p>Contains all information about newly created tenant.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantOnboardingResponse {

  private UUID organizationId;

  private UUID tenantId;

  private String organizationUid;

  private String organizationName;

  private UUID adminUserId;

  private String adminContactValue;

  private String registrationToken;

  private List<String> subscriptions;

  private Instant trialEndsAt;

  private String setupUrl;
}
