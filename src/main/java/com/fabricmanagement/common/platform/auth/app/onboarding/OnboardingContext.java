package com.fabricmanagement.common.platform.auth.app.onboarding;

import com.fabricmanagement.common.platform.auth.dto.TenantOnboardingResponse;
import com.fabricmanagement.common.platform.organization.domain.OrganizationType;
import com.fabricmanagement.common.platform.user.dto.CreateAdminUserRequest;
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
  private String companyName;
  private String taxId;
  private OrganizationType companyType;
  private String address;
  private String city;
  private String country;
  private String phoneNumber;
  private String companyEmail;
  private String adminFirstName;
  private String adminLastName;
  private String adminContact;
  private String adminDepartment;
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
  // OUTPUTS - Legacy/Backward Compatibility
  // ========================================
  /**
   * @deprecated Use organizationId instead. Kept for backward compatibility.
   */
  @Deprecated private UUID companyId;

  /**
   * @deprecated Use organizationUid instead. Kept for backward compatibility.
   */
  @Deprecated private String companyUid;

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
        .companyId(organizationId != null ? organizationId : companyId)
        .tenantId(tenantId)
        .firstName(adminFirstName)
        .lastName(adminLastName)
        .contactValue(adminContact)
        .department(adminDepartment)
        .build();
  }

  public TenantOnboardingResponse toResult() {
    return TenantOnboardingResponse.builder()
        .companyId(organizationId != null ? organizationId : companyId)
        .tenantId(tenantId)
        .companyUid(organizationUid != null ? organizationUid : companyUid)
        .companyName(companyName)
        .adminUserId(userId)
        .adminContactValue(adminContactValue != null ? adminContactValue : adminContact)
        .registrationToken(registrationToken)
        .subscriptions(subscriptionOsCodes)
        .trialEndsAt(trialEndsAt)
        .setupUrl(setupUrl)
        .build();
  }

  // ========================================
  // BACKWARD COMPATIBILITY GETTERS/SETTERS
  // ========================================

  /** Gets companyId for backward compatibility. Returns organizationId if set. */
  public UUID getCompanyId() {
    return organizationId != null ? organizationId : companyId;
  }

  /** Sets companyId for backward compatibility. Also sets organizationId. */
  public void setCompanyId(UUID companyId) {
    this.companyId = companyId;
    if (this.organizationId == null) {
      this.organizationId = companyId;
    }
  }

  /** Gets companyUid for backward compatibility. Returns organizationUid if set. */
  public String getCompanyUid() {
    return organizationUid != null ? organizationUid : companyUid;
  }

  /** Sets companyUid for backward compatibility. Also sets organizationUid. */
  public void setCompanyUid(String companyUid) {
    this.companyUid = companyUid;
    if (this.organizationUid == null) {
      this.organizationUid = companyUid;
    }
  }
}
