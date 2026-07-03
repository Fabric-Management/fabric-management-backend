package com.fabricmanagement.platform.auth.api.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.security.AuthCookieSupport;
import com.fabricmanagement.platform.auth.app.ExistingAccountOrganizationService;
import com.fabricmanagement.platform.auth.app.JwtService;
import com.fabricmanagement.platform.auth.app.LoginService;
import com.fabricmanagement.platform.auth.app.LogoutService;
import com.fabricmanagement.platform.auth.app.MfaEventService;
import com.fabricmanagement.platform.auth.app.MfaSetupService;
import com.fabricmanagement.platform.auth.app.RefreshTokenService;
import com.fabricmanagement.platform.auth.app.SwitchOrganizationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private LoginService loginService;
  @MockBean private LogoutService logoutService;
  @MockBean private RefreshTokenService refreshTokenService;
  @MockBean private SwitchOrganizationService switchOrganizationService;
  @MockBean private ExistingAccountOrganizationService existingAccountOrganizationService;
  @MockBean private JwtService jwtService;
  @MockBean private MfaSetupService mfaSetupService;
  @MockBean private MfaEventService mfaEventService;
  @MockBean private AuthCookieSupport authCookieSupport;

  // The web slice still registers the global interceptors (JwtContextInterceptor etc.), whose
  // dependencies live outside the slice — mock them like the sibling controller tests do.
  @MockBean
  private com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort tenantQueryPort;

  @MockBean
  private com.fabricmanagement.platform.tenant.infra.repository.TenantRepository tenantRepository;

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void createOrganizationRejectsAnonymousCaller() throws Exception {
    String body =
        """
        {
          "organizationName": "Sirket B",
          "organizationType": "WEAVER"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/auth/organizations")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_NOT_AUTHENTICATED"));
  }
}
