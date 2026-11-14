package com.fabricmanagement.platform.auth.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.platform.auth.app.JwtService;
import com.fabricmanagement.platform.auth.app.SignupRequestService;
import com.fabricmanagement.platform.auth.dto.SelfSignupRequest;
import com.fabricmanagement.platform.auth.dto.SignupIntent;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = PublicSignupController.class)
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
@DisplayName("PublicSignupController")
@ActiveProfiles("test")
class PublicSignupControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private OrganizationRepository organizationRepository;

  @MockitoBean
  private com.fabricmanagement.platform.tenant.infra.repository.TenantRepository tenantRepository;

  @MockitoBean private JwtService jwtService;

  @MockitoBean
  private com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort tenantQueryPort;

  @MockitoBean private SignupRequestService signupRequestService;

  @MockitoBean
  private com.fabricmanagement.platform.communication.app.NotificationService notificationService;

  @MockitoBean
  private com.fabricmanagement.platform.communication.app.EmailTemplateRenderer
      emailTemplateRenderer;

  @MockitoBean
  private com.fabricmanagement.common.infrastructure.config.FrontendUrlProvider frontendUrlProvider;

  @Test
  @DisplayName("POST /api/v1/public/signup returns 200 and success message")
  void signup_returnsOkAndSuccessMessage() throws Exception {
    String body =
        """
        {
          "organizationName": "Signup Co",
          "taxId": "9876543210",
          "organizationType": "WEAVER",
          "firstName": "John",
          "lastName": "Doe",
          "email": "user@example.com",
          "acceptedTerms": true
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/public/signup")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").value("Welcome! Check your email to complete registration."));
  }

  @Test
  @DisplayName("POST /api/v1/public/signup defaults unknown intent to playground")
  void signup_defaultsUnknownIntentToPlayground() throws Exception {
    String body =
        """
        {
          "organizationName": "Signup Co",
          "taxId": "9876543210",
          "organizationType": "WEAVER",
          "firstName": "John",
          "lastName": "Doe",
          "email": "user@example.com",
          "acceptedTerms": true,
          "intent": "something-else"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/public/signup")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());

    ArgumentCaptor<SelfSignupRequest> requestCaptor =
        ArgumentCaptor.forClass(SelfSignupRequest.class);
    verify(signupRequestService).request(requestCaptor.capture());
    assertThat(requestCaptor.getValue().getIntent()).isEqualTo(SignupIntent.PLAYGROUND);
  }

  @Test
  @DisplayName("POST /api/v1/public/signup refuses a value too long for the pending signup row")
  void signup_refusesTooLongTaxId() throws Exception {
    String body =
        """
        {
          "organizationName": "Signup Co",
          "taxId": "%s",
          "organizationType": "WEAVER",
          "firstName": "John",
          "lastName": "Doe",
          "email": "user@example.com",
          "acceptedTerms": true
        }
        """
            .formatted("9".repeat(51));

    mockMvc
        .perform(
            post("/api/v1/public/signup")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors.taxId").exists());

    verifyNoInteractions(signupRequestService);
  }
}
