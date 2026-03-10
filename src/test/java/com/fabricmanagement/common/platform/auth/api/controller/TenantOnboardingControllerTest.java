package com.fabricmanagement.common.platform.auth.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.platform.auth.app.JwtService;
import com.fabricmanagement.common.platform.auth.app.TenantOnboardingService;
import com.fabricmanagement.common.platform.auth.dto.TenantOnboardingRequest;
import com.fabricmanagement.common.platform.auth.dto.TenantOnboardingResponse;
import com.fabricmanagement.common.platform.organization.infra.repository.OrganizationRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = TenantOnboardingController.class)
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
@DisplayName("TenantOnboardingController")
class TenantOnboardingControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private OrganizationRepository organizationRepository;

  @MockBean
  private com.fabricmanagement.common.platform.tenant.infra.repository.TenantRepository
      tenantRepository;

  @MockBean private JwtService jwtService;
  @MockBean private TenantOnboardingService onboardingService;

  @Test
  @DisplayName("POST /api/admin/onboarding/tenant returns 200 and onboarding response")
  void createTenant_returnsOkAndResponse() throws Exception {
    UUID organizationId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    TenantOnboardingResponse response =
        TenantOnboardingResponse.builder()
            .organizationId(organizationId)
            .tenantId(tenantId)
            .organizationUid("ACME-001")
            .organizationName("Acme Corp")
            .adminUserId(userId)
            .adminContactValue("admin@acme.com")
            .registrationToken("token-123")
            .subscriptions(List.of("FabricOS"))
            .trialEndsAt(Instant.now().plusSeconds(86400))
            .setupUrl("https://app.example.com/setup?token=token-123")
            .build();
    when(onboardingService.createSalesLedTenant(any(TenantOnboardingRequest.class)))
        .thenReturn(response);

    String body =
        """
        {
          "organizationName": "Acme Corp",
          "taxId": "1234567890",
          "organizationType": "SPINNER",
          "adminFirstName": "Jane",
          "adminLastName": "Doe",
          "adminContact": "admin@acme.com",
          "trialDays": 90
        }
        """;

    mockMvc
        .perform(
            post("/api/admin/onboarding/tenant")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.organizationId").value(organizationId.toString()))
        .andExpect(jsonPath("$.data.tenantId").value(tenantId.toString()))
        .andExpect(jsonPath("$.data.organizationUid").value("ACME-001"))
        .andExpect(jsonPath("$.data.organizationName").value("Acme Corp"))
        .andExpect(jsonPath("$.data.registrationToken").value("token-123"))
        .andExpect(
            jsonPath("$.data.setupUrl").value("https://app.example.com/setup?token=token-123"));
  }
}
