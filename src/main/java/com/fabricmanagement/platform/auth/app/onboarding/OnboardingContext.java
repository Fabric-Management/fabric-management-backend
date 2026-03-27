package com.fabricmanagement.platform.auth.app.onboarding;

import com.fabricmanagement.platform.auth.dto.TenantOnboardingResponse;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.platform.user.dto.CreateAdminUserRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Data;

/**
 * Shared context for onboarding steps. Holds request data and outputs produced by steps.
 *
 * <p>Steps read request fields and set result fields (tenantId, organizationId, userId, token,
 * etc.). Orchestrator builds {@link TenantOnboardingResponse} from context via {@link #toResult()}.
 *
 * <h2>New Architecture (Faz 2):</h2>
 *
 * <ul>
 *   <li>Step 1 (CreateTenantStep): Creates Tenant entity → sets tenantId, tenantUid
 *   <li>Step 2 (CreateOrganizationStep): Creates Organization → sets organizationId,
 *       organizationUid
 *   <li>Step 3+ (CreateAdminUserStep, etc.): Use tenantId and organizationId
 * </ul>
 */
@Data
public class OnboardingContext {

  // ========================================
  // REQUEST DATA (sales-led or self-service)
  // ========================================
  private String organizationName;
  private String taxId;
  private OrganizationType organizationType;
  private String address;
  private String city;
  private String state;
  private String district;
  private String postalCode;
  private String country;
  private String phoneNumber;
  private String organizationEmail;
  private String adminFirstName;
  private String adminLastName;
  private String adminContact;
  private List<String> selectedOS;
  private int trialDays;
  private boolean salesLed;

  // ========================================
  // OUTPUTS - Tenant (Step 1)
  // ========================================
  /** Tenant UUID - set by CreateTenantStep */
  private UUID tenantId;

  /** Tenant UID (e.g., "ACME-001") - set by CreateTenantStep */
  private String tenantUid;

  // ========================================
  // OUTPUTS - Organization (Step 2)
  // ========================================
  /** Organization UUID - set by CreateOrganizationStep */
  private UUID organizationId;

  /** Organization UID - set by CreateOrganizationStep */
  private String organizationUid;

  // ========================================
  // OUTPUTS - User (Step 3)
  // ========================================
  private UUID userId;
  private String adminContactValue;
  private String registrationToken;

  // ========================================
  // OUTPUTS - Subscriptions (Step 4)
  // ========================================
  private List<String> subscriptionOsCodes;
  private Instant trialEndsAt;
  private String setupUrl;

  // ========================================
  // HELPER METHODS
  // ========================================

  public CreateAdminUserRequest toCreateAdminUserRequest() {
    return CreateAdminUserRequest.builder()
        .organizationId(organizationId)
        .tenantId(tenantId)
        .firstName(adminFirstName)
        .lastName(adminLastName)
        .contactValue(adminContact)
        .build();
  }

  public TenantOnboardingResponse toResult() {
    return TenantOnboardingResponse.builder()
        .organizationId(organizationId)
        .tenantId(tenantId)
        .organizationUid(organizationUid)
        .organizationName(organizationName)
        .adminUserId(userId)
        .adminContactValue(adminContactValue != null ? adminContactValue : adminContact)
        .registrationToken(registrationToken)
        .subscriptions(subscriptionOsCodes)
        .trialEndsAt(trialEndsAt)
        .setupUrl(setupUrl)
        .build();
  }
}
