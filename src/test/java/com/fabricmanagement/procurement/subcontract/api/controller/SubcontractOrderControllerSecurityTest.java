package com.fabricmanagement.procurement.subcontract.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.security.SpELPermissionEvaluator;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.procurement.subcontract.app.SubcontractOrderService;
import com.fabricmanagement.procurement.subcontract.domain.SubcontractOrderStatus;
import com.fabricmanagement.procurement.subcontract.dto.SubcontractOrderResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Pageable;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SubcontractOrderController.class)
@EnableMethodSecurity
class SubcontractOrderControllerSecurityTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private SubcontractOrderService subcontractOrderService;
  @MockBean private com.fabricmanagement.platform.auth.app.JwtService jwtService;

  @MockBean
  private com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort tenantQueryPort;

  @MockBean(name = "auth")
  private SpELPermissionEvaluator authEvaluator;

  @Test
  void shouldReturn401WhenUnauthenticated() throws Exception {
    mockMvc
        .perform(get("/api/v1/procurement/subcontract-orders"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser
  void shouldReturn403WhenMissingProcurementReadPermission() throws Exception {
    when(authEvaluator.can(any(Authentication.class), eq("procurement"), eq("read")))
        .thenReturn(false);

    mockMvc
        .perform(get("/api/v1/procurement/subcontract-orders"))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser
  void shouldReturnPagedSubcontractOrdersAndBindStatusFilter() throws Exception {
    UUID subcontractOrderId = UUID.randomUUID();
    when(authEvaluator.can(any(Authentication.class), eq("procurement"), eq("read")))
        .thenReturn(true);
    when(subcontractOrderService.listSubcontractOrders(eq(SubcontractOrderStatus.CONFIRMED), any()))
        .thenReturn(
            PagedResponse.<SubcontractOrderResponse>builder()
                .content(List.of(response(subcontractOrderId, SubcontractOrderStatus.CONFIRMED)))
                .page(0)
                .size(10)
                .totalElements(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .build());

    mockMvc
        .perform(
            get("/api/v1/procurement/subcontract-orders")
                .param("status", "CONFIRMED")
                .param("page", "0")
                .param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content[0].id").value(subcontractOrderId.toString()))
        .andExpect(jsonPath("$.data.content[0].status").value("CONFIRMED"))
        .andExpect(jsonPath("$.data.totalElements").value(1));

    verify(subcontractOrderService)
        .listSubcontractOrders(eq(SubcontractOrderStatus.CONFIRMED), any(Pageable.class));
  }

  private SubcontractOrderResponse response(UUID id, SubcontractOrderStatus status) {
    return SubcontractOrderResponse.builder()
        .id(id)
        .scNumber("SC-20260624-00001")
        .workOrderId(UUID.randomUUID())
        .tradingPartnerId(UUID.randomUUID())
        .status(status)
        .expectedReturnDate(LocalDate.now().plusDays(7))
        .build();
  }
}
