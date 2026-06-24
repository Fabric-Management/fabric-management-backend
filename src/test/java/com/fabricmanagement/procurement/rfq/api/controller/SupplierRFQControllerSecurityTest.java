package com.fabricmanagement.procurement.rfq.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.security.SpELPermissionEvaluator;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.procurement.rfq.app.SupplierRFQService;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQModuleType;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQStatus;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQType;
import com.fabricmanagement.procurement.rfq.dto.SupplierRFQResponse;
import java.time.Instant;
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

@WebMvcTest(SupplierRFQController.class)
@EnableMethodSecurity
class SupplierRFQControllerSecurityTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private SupplierRFQService rfqService;
  @MockBean private com.fabricmanagement.platform.auth.app.JwtService jwtService;

  @MockBean
  private com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort tenantQueryPort;

  @MockBean(name = "auth")
  private SpELPermissionEvaluator authEvaluator;

  @Test
  void shouldReturn401WhenUnauthenticated() throws Exception {
    mockMvc.perform(get("/api/v1/procurement/rfqs")).andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser
  void shouldReturn403WhenMissingProcurementReadPermission() throws Exception {
    when(authEvaluator.can(any(Authentication.class), eq("procurement"), eq("read")))
        .thenReturn(false);

    mockMvc.perform(get("/api/v1/procurement/rfqs")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser
  void shouldReturnRfqDetailWhenUserHasProcurementRead() throws Exception {
    UUID rfqId = UUID.randomUUID();
    when(authEvaluator.can(any(Authentication.class), eq("procurement"), eq("read")))
        .thenReturn(true);
    when(rfqService.getRfq(rfqId)).thenReturn(response(rfqId, SupplierRFQStatus.DRAFT));

    mockMvc
        .perform(get("/api/v1/procurement/rfqs/{id}", rfqId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(rfqId.toString()))
        .andExpect(jsonPath("$.data.status").value("DRAFT"));
  }

  @Test
  @WithMockUser
  void shouldReturnPagedRfqsAndBindStatusFilter() throws Exception {
    UUID rfqId = UUID.randomUUID();
    when(authEvaluator.can(any(Authentication.class), eq("procurement"), eq("read")))
        .thenReturn(true);
    when(rfqService.listRfqs(eq(SupplierRFQStatus.SENT), eq(SupplierRFQModuleType.FIBER), any()))
        .thenReturn(
            PagedResponse.<SupplierRFQResponse>builder()
                .content(List.of(response(rfqId, SupplierRFQStatus.SENT)))
                .page(0)
                .size(10)
                .totalElements(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .build());

    mockMvc
        .perform(
            get("/api/v1/procurement/rfqs")
                .param("status", "SENT")
                .param("moduleType", "FIBER")
                .param("page", "0")
                .param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content[0].id").value(rfqId.toString()))
        .andExpect(jsonPath("$.data.content[0].status").value("SENT"))
        .andExpect(jsonPath("$.data.totalElements").value(1));

    verify(rfqService)
        .listRfqs(eq(SupplierRFQStatus.SENT), eq(SupplierRFQModuleType.FIBER), any(Pageable.class));
  }

  private SupplierRFQResponse response(UUID id, SupplierRFQStatus status) {
    return SupplierRFQResponse.builder()
        .id(id)
        .rfqNumber("RFQ-2026-TEST")
        .workOrderId(UUID.randomUUID())
        .moduleType(SupplierRFQModuleType.FIBER)
        .rfqType(SupplierRFQType.PURCHASE)
        .status(status)
        .deadline(Instant.now())
        .lines(List.of())
        .recipients(List.of())
        .createdAt(Instant.now())
        .build();
  }
}
