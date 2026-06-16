package com.fabricmanagement.finance.payables.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.security.SpELPermissionEvaluator;
import com.fabricmanagement.finance.payables.app.PayablesInsightService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PayablesInsightController.class)
@EnableMethodSecurity
class PayablesInsightControllerSecurityTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private PayablesInsightService payablesInsightService;
  @MockBean private com.fabricmanagement.platform.auth.app.JwtService jwtService;

  @MockBean
  private com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort tenantQueryPort;

  @MockBean(name = "auth")
  private SpELPermissionEvaluator authEvaluator;

  @Test
  @WithMockUser
  void whenUserHasFinanceRead_thenSummaryAndSuppliersReturn200() throws Exception {
    when(authEvaluator.can(any(Authentication.class), eq("finance"), eq("read"))).thenReturn(true);
    when(payablesInsightService.getSuppliers(any())).thenReturn(new PageImpl<>(List.of()));

    mockMvc.perform(get("/api/v1/finance/payables/summary")).andExpect(status().isOk());
    mockMvc.perform(get("/api/v1/finance/payables/suppliers")).andExpect(status().isOk());
  }

  @Test
  @WithMockUser
  void whenUserLacksFinanceRead_thenEndpointsReturn403() throws Exception {
    when(authEvaluator.can(any(Authentication.class), eq("finance"), eq("read"))).thenReturn(false);

    mockMvc.perform(get("/api/v1/finance/payables/summary")).andExpect(status().isForbidden());
    mockMvc.perform(get("/api/v1/finance/payables/suppliers")).andExpect(status().isForbidden());
  }
}
