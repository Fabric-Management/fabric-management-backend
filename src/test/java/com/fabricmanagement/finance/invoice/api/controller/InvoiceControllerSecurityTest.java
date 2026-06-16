package com.fabricmanagement.finance.invoice.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.security.SpELPermissionEvaluator;
import com.fabricmanagement.finance.invoice.app.CreditNoteApplicationService;
import com.fabricmanagement.finance.invoice.app.InvoiceService;
import com.fabricmanagement.finance.payment.app.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InvoiceController.class)
@EnableMethodSecurity
class InvoiceControllerSecurityTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockBean private InvoiceService invoiceService;
  @MockBean private PaymentService paymentService;
  @MockBean private CreditNoteApplicationService creditNoteApplicationService;
  @MockBean private com.fabricmanagement.platform.auth.app.JwtService jwtService;

  @MockBean
  private com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort tenantQueryPort;

  @MockBean(name = "auth")
  private SpELPermissionEvaluator authEvaluator;

  @Test
  @WithMockUser
  void whenUserHasFinanceRead_thenGetReturns200_butPostReturns403() throws Exception {
    // Setup Auth Evaluator: user has 'finance:read' but not 'finance:write'
    when(authEvaluator.can(any(Authentication.class), eq("finance"), eq("read"))).thenReturn(true);
    when(authEvaluator.can(any(Authentication.class), eq("finance"), eq("write")))
        .thenReturn(false);

    // GET should succeed (200 OK)
    // Create an empty mock invoice
    when(invoiceService.getInvoice(any(UUID.class))).thenReturn(null);
    mockMvc
        .perform(get("/api/v1/finance/invoices/" + UUID.randomUUID()))
        .andExpect(status().isOk());

    // POST should fail (403 Forbidden)
    mockMvc
        .perform(
            post("/api/v1/finance/invoices").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser
  void whenUserHasOnlySalesAccess_thenFinanceEndpointsReturn403() throws Exception {
    // Setup Auth Evaluator: user has 'sales:read' and 'sales:write', but NO finance access
    when(authEvaluator.can(any(Authentication.class), eq("sales"), any())).thenReturn(true);
    when(authEvaluator.can(any(Authentication.class), eq("finance"), any())).thenReturn(false);

    // GET should fail
    mockMvc
        .perform(get("/api/v1/finance/invoices/" + UUID.randomUUID()))
        .andExpect(status().isForbidden());

    // POST should fail
    mockMvc
        .perform(
            post("/api/v1/finance/invoices").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isForbidden());
  }
}
