package com.fabricmanagement.analytics.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.analytics.app.RevenueBacklogService;
import com.fabricmanagement.common.infrastructure.security.SpELPermissionEvaluator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RevenueBacklogController.class)
@EnableMethodSecurity
class RevenueBacklogControllerSecurityTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private RevenueBacklogService revenueBacklogService;
  @MockBean private com.fabricmanagement.platform.auth.app.JwtService jwtService;

  @MockBean
  private com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort tenantQueryPort;

  @MockBean(name = "auth")
  private SpELPermissionEvaluator authEvaluator;

  @Test
  void shouldReturn401WhenUnauthenticated() throws Exception {
    mockMvc.perform(get("/api/v1/analytics/trends")).andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser
  void shouldReturn403WhenMissingFinanceReadPermission() throws Exception {
    when(authEvaluator.can(any(Authentication.class), eq("finance"), eq("read"))).thenReturn(false);

    mockMvc.perform(get("/api/v1/analytics/trends")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser
  void shouldReturn200WhenHasFinanceReadPermission() throws Exception {
    when(authEvaluator.can(any(Authentication.class), eq("finance"), eq("read"))).thenReturn(true);

    mockMvc.perform(get("/api/v1/analytics/trends")).andExpect(status().isOk());
  }
}
