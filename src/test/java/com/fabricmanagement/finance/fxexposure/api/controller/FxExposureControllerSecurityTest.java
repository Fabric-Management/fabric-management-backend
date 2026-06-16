package com.fabricmanagement.finance.fxexposure.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.security.SpELPermissionEvaluator;
import com.fabricmanagement.finance.fxexposure.app.FxExposureService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FxExposureController.class)
@EnableMethodSecurity
class FxExposureControllerSecurityTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private FxExposureService fxExposureService;

  @MockBean private com.fabricmanagement.platform.auth.app.JwtService jwtService;

  @MockBean
  private com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort tenantQueryPort;

  @MockBean(name = "auth")
  private SpELPermissionEvaluator authEvaluator;

  private final UUID tenantId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    // Basic setup if needed
  }

  @Test
  @WithMockUser
  void getSummary_withFinanceRead_isOk() throws Exception {
    when(authEvaluator.can(any(Authentication.class), eq("finance"), eq("read"))).thenReturn(true);

    mockMvc.perform(get("/api/v1/finance/fx-exposure/summary")).andExpect(status().isOk());
  }

  @Test
  @WithMockUser
  void getSummary_withoutFinanceRead_isForbidden() throws Exception {
    when(authEvaluator.can(any(Authentication.class), eq("finance"), eq("read"))).thenReturn(false);

    mockMvc.perform(get("/api/v1/finance/fx-exposure/summary")).andExpect(status().isForbidden());
  }

  @Test
  void getSummary_unauthenticated_isUnauthorized() throws Exception {
    mockMvc
        .perform(get("/api/v1/finance/fx-exposure/summary"))
        .andExpect(status().isUnauthorized());
  }
}
