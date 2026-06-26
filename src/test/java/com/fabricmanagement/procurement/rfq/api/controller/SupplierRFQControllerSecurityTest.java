package com.fabricmanagement.procurement.rfq.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.security.SpELPermissionEvaluator;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.procurement.rfq.app.SupplierRFQService;
import com.fabricmanagement.procurement.rfq.domain.RfqRecipientStatus;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQModuleType;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQStatus;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQType;
import com.fabricmanagement.procurement.rfq.dto.AddRecipientRequest;
import com.fabricmanagement.procurement.rfq.dto.AddRfqLineRequest;
import com.fabricmanagement.procurement.rfq.dto.CreateSupplierRFQRequest;
import com.fabricmanagement.procurement.rfq.dto.SupplierRFQResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SupplierRFQController.class)
@EnableMethodSecurity
class SupplierRFQControllerSecurityTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

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

  @Test
  @WithMockUser
  void shouldCreateLineRecipientAndSendRfqWithPopulatedDto() throws Exception {
    UUID rfqId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    UUID partnerId = UUID.randomUUID();
    when(authEvaluator.can(any(Authentication.class), eq("procurement"), eq("write")))
        .thenReturn(true);

    when(rfqService.createRfq(any(CreateSupplierRFQRequest.class)))
        .thenReturn(response(rfqId, SupplierRFQStatus.DRAFT));
    when(rfqService.addLine(eq(rfqId), any(AddRfqLineRequest.class)))
        .thenReturn(responseWithLine(rfqId, SupplierRFQStatus.DRAFT, productId));
    when(rfqService.addRecipient(eq(rfqId), any(AddRecipientRequest.class)))
        .thenReturn(
            responseWithLineAndRecipient(rfqId, SupplierRFQStatus.DRAFT, productId, partnerId));
    when(rfqService.sendRfq(rfqId))
        .thenReturn(
            responseWithLineAndRecipient(rfqId, SupplierRFQStatus.SENT, productId, partnerId));

    CreateSupplierRFQRequest createReq = new CreateSupplierRFQRequest();
    createReq.setWorkOrderId(UUID.randomUUID());
    createReq.setModuleType(SupplierRFQModuleType.FIBER);
    createReq.setRfqType(SupplierRFQType.PURCHASE);
    createReq.setDeadline(Instant.now().plusSeconds(604800));

    mockMvc
        .perform(
            post("/api/v1/procurement/rfqs")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createReq)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(rfqId.toString()))
        .andExpect(jsonPath("$.status").value("DRAFT"));

    mockMvc
        .perform(
            post("/api/v1/procurement/rfqs/{id}/lines", rfqId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new AddRfqLineRequest(
                            productId, "BCI cotton", new BigDecimal("1000"), "KG", null))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.lines.length()").value(1))
        .andExpect(jsonPath("$.lines[0].productId").value(productId.toString()));

    AddRecipientRequest recipientReq = new AddRecipientRequest();
    recipientReq.setTradingPartnerId(partnerId);
    mockMvc
        .perform(
            post("/api/v1/procurement/rfqs/{id}/recipients", rfqId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(recipientReq)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.lines.length()").value(1))
        .andExpect(jsonPath("$.recipients.length()").value(1))
        .andExpect(jsonPath("$.recipients[0].tradingPartnerId").value(partnerId.toString()));

    mockMvc
        .perform(post("/api/v1/procurement/rfqs/{id}/send", rfqId).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SENT"))
        .andExpect(jsonPath("$.lines.length()").value(1))
        .andExpect(jsonPath("$.recipients.length()").value(1))
        .andExpect(jsonPath("$.recipients[0].status").value("SENT"));
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

  private SupplierRFQResponse responseWithLine(UUID id, SupplierRFQStatus status, UUID productId) {
    return SupplierRFQResponse.builder()
        .id(id)
        .rfqNumber("RFQ-2026-TEST")
        .workOrderId(UUID.randomUUID())
        .moduleType(SupplierRFQModuleType.FIBER)
        .rfqType(SupplierRFQType.PURCHASE)
        .status(status)
        .deadline(Instant.now())
        .lines(
            List.of(
                SupplierRFQResponse.RfqLineResponse.builder()
                    .id(UUID.randomUUID())
                    .productId(productId)
                    .productDesc("BCI cotton")
                    .requestedQty(new BigDecimal("1000"))
                    .unit("KG")
                    .build()))
        .recipients(List.of())
        .createdAt(Instant.now())
        .build();
  }

  private SupplierRFQResponse responseWithLineAndRecipient(
      UUID id, SupplierRFQStatus status, UUID productId, UUID partnerId) {
    SupplierRFQResponse lineResponse = responseWithLine(id, status, productId);
    return SupplierRFQResponse.builder()
        .id(lineResponse.getId())
        .rfqNumber(lineResponse.getRfqNumber())
        .workOrderId(lineResponse.getWorkOrderId())
        .moduleType(lineResponse.getModuleType())
        .rfqType(lineResponse.getRfqType())
        .status(lineResponse.getStatus())
        .deadline(lineResponse.getDeadline())
        .lines(lineResponse.getLines())
        .recipients(
            List.of(
                SupplierRFQResponse.RecipientResponse.builder()
                    .id(UUID.randomUUID())
                    .tradingPartnerId(partnerId)
                    .status(
                        status == SupplierRFQStatus.SENT
                            ? RfqRecipientStatus.SENT
                            : RfqRecipientStatus.PENDING)
                    .build()))
        .createdAt(lineResponse.getCreatedAt())
        .build();
  }
}
