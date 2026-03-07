package com.fabricmanagement.common.platform.auth.app.onboarding;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.platform.auth.dto.TenantOnboardingResponse;
import com.fabricmanagement.common.platform.user.dto.CreateAdminUserRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("OnboardingContext")
class OnboardingContextTest {

  @Test
  void toCreateAdminUserRequest_buildsFromContext() {
    UUID organizationId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    OnboardingContext context = new OnboardingContext();
    context.setOrganizationId(organizationId);
    context.setTenantId(tenantId);
    context.setAdminFirstName("Jane");
    context.setAdminLastName("Doe");
    context.setAdminContact("jane@acme.com");
    context.setAdminDepartment("IT");

    CreateAdminUserRequest request = context.toCreateAdminUserRequest();

    assertThat(request.getOrganizationId()).isEqualTo(organizationId);
    assertThat(request.getTenantId()).isEqualTo(tenantId);
    assertThat(request.getFirstName()).isEqualTo("Jane");
    assertThat(request.getLastName()).isEqualTo("Doe");
    assertThat(request.getContactValue()).isEqualTo("jane@acme.com");
    assertThat(request.getDepartment()).isEqualTo("IT");
  }

  @Test
  void toResult_buildsResponseFromContext() {
    UUID organizationId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    OnboardingContext context = new OnboardingContext();
    context.setCompanyName("Acme");
    context.setOrganizationId(organizationId);
    context.setTenantId(tenantId);
    context.setOrganizationUid("ACME-001");
    context.setUserId(userId);
    context.setAdminContactValue("admin@acme.com");
    context.setRegistrationToken("token-123");
    context.setSubscriptionOsCodes(List.of("FabricOS"));
    context.setTrialEndsAt(Instant.now().plusSeconds(86400));
    context.setSetupUrl("https://app.example.com/setup?token=token-123");

    TenantOnboardingResponse result = context.toResult();

    assertThat(result.getCompanyId()).isEqualTo(organizationId);
    assertThat(result.getTenantId()).isEqualTo(tenantId);
    assertThat(result.getCompanyUid())
        .isEqualTo("ACME-001"); // TenantOnboardingResponse still uses companyUid field name
    assertThat(result.getCompanyName()).isEqualTo("Acme");
    assertThat(result.getAdminUserId()).isEqualTo(userId);
    assertThat(result.getAdminContactValue()).isEqualTo("admin@acme.com");
    assertThat(result.getRegistrationToken()).isEqualTo("token-123");
    assertThat(result.getSubscriptions()).containsExactly("FabricOS");
    assertThat(result.getSetupUrl()).isEqualTo("https://app.example.com/setup?token=token-123");
  }

  @Test
  void toResult_usesAdminContactWhenAdminContactValueNull() {
    OnboardingContext context = new OnboardingContext();
    context.setAdminContact("fallback@acme.com");
    context.setAdminContactValue(null);

    TenantOnboardingResponse result = context.toResult();

    assertThat(result.getAdminContactValue()).isEqualTo("fallback@acme.com");
  }
}
