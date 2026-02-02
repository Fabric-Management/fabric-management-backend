package com.fabricmanagement.common.platform.auth.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.platform.auth.app.JwtService;
import com.fabricmanagement.common.platform.auth.app.TenantOnboardingService;
import com.fabricmanagement.common.platform.auth.dto.SelfSignupRequest;
import com.fabricmanagement.common.platform.auth.dto.TenantOnboardingResponse;
import com.fabricmanagement.common.platform.company.infra.repository.CompanyRepository;
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

@WebMvcTest(controllers = PublicSignupController.class)
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
@DisplayName("PublicSignupController")
class PublicSignupControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private CompanyRepository companyRepository;

  @MockBean
  private com.fabricmanagement.common.platform.tenant.infra.repository.TenantRepository
      tenantRepository;

  @MockBean private JwtService jwtService;
  @MockBean private TenantOnboardingService onboardingService;

  @MockBean
  private com.fabricmanagement.common.platform.communication.app.NotificationService
      notificationService;

  @MockBean
  private com.fabricmanagement.common.platform.communication.app.EmailTemplateRenderer
      emailTemplateRenderer;

  @MockBean
  private com.fabricmanagement.common.infrastructure.config.FrontendUrlProvider frontendUrlProvider;

  @Test
  @DisplayName("POST /api/public/signup returns 200 and success message")
  void signup_returnsOkAndSuccessMessage() throws Exception {
    UUID companyId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    TenantOnboardingResponse response =
        TenantOnboardingResponse.builder()
            .companyId(companyId)
            .tenantId(tenantId)
            .companyUid("SIGNUP-001")
            .companyName("Signup Co")
            .adminUserId(UUID.randomUUID())
            .adminContactValue("user@example.com")
            .registrationToken("token-456")
            .subscriptions(List.of("FabricOS"))
            .trialEndsAt(Instant.now().plusSeconds(86400))
            .setupUrl("https://app.example.com/setup?token=token-456")
            .build();
    when(onboardingService.createSelfServiceTenant(any(SelfSignupRequest.class)))
        .thenReturn(response);

    String body =
        """
        {
          "companyName": "Signup Co",
          "taxId": "9876543210",
          "companyType": "WEAVER",
          "firstName": "John",
          "lastName": "Doe",
          "email": "user@example.com",
          "acceptedTerms": true
        }
        """;

    mockMvc
        .perform(
            post("/api/public/signup")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").value("Welcome! Check your email to complete registration."));
  }
}
