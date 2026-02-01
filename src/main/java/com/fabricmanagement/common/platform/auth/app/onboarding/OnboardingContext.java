package com.fabricmanagement.common.platform.auth.app.onboarding;

import com.fabricmanagement.common.platform.auth.dto.TenantOnboardingResponse;
import com.fabricmanagement.common.platform.company.domain.CompanyType;
import com.fabricmanagement.common.platform.company.dto.CreateTenantCompanyRequest;
import com.fabricmanagement.common.platform.user.dto.CreateAdminUserRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Data;

/**
 * Shared context for onboarding steps. Holds request data and outputs produced by steps.
 *
 * <p>Steps read request fields and set result fields (companyId, tenantId, userId, token, etc.).
 * Orchestrator builds {@link TenantOnboardingResponse} from context via {@link #toResult()}.
 */
@Data
public class OnboardingContext {

  // ---- Request (sales-led or self-service) ----
  private String companyName;
  private String taxId;
  private CompanyType companyType;
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

  // ---- Outputs (set by steps) ----
  private UUID companyId;
  private UUID tenantId;
  private String companyUid;
  private UUID userId;
  private String adminContactValue;
  private String registrationToken;
  private List<String> subscriptionOsCodes;
  private Instant trialEndsAt;
  private String setupUrl;

  public CreateTenantCompanyRequest toCreateTenantCompanyRequest() {
    return CreateTenantCompanyRequest.builder()
        .companyName(companyName)
        .taxId(taxId)
        .companyType(companyType)
        .build();
  }

  public CreateAdminUserRequest toCreateAdminUserRequest() {
    return CreateAdminUserRequest.builder()
        .companyId(companyId)
        .tenantId(tenantId)
        .firstName(adminFirstName)
        .lastName(adminLastName)
        .contactValue(adminContact)
        .department(adminDepartment)
        .build();
  }

  public TenantOnboardingResponse toResult() {
    return TenantOnboardingResponse.builder()
        .companyId(companyId)
        .tenantId(tenantId)
        .companyUid(companyUid)
        .companyName(companyName)
        .adminUserId(userId)
        .adminContactValue(adminContactValue != null ? adminContactValue : adminContact)
        .registrationToken(registrationToken)
        .subscriptions(subscriptionOsCodes)
        .trialEndsAt(trialEndsAt)
        .setupUrl(setupUrl)
        .build();
  }
}
