package com.fabricmanagement.sales.ownership.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.SpELPermissionEvaluator;
import com.fabricmanagement.sales.ownership.app.CustomerAccountTeamService;
import com.fabricmanagement.sales.ownership.domain.OwnerResolutionReason;
import com.fabricmanagement.sales.ownership.dto.CustomerAccountTeamResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CustomerAccountTeamController.class)
@EnableMethodSecurity
class CustomerAccountTeamControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockBean private CustomerAccountTeamService accountTeamService;
  @MockBean private com.fabricmanagement.platform.auth.app.JwtService jwtService;

  @MockBean
  private com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort tenantQueryPort;

  @MockBean(name = "auth")
  private SpELPermissionEvaluator authEvaluator;

  private UUID tenantId;

  @BeforeEach
  void setUp() {
    tenantId = UUID.randomUUID();
    TenantContext.setCurrentTenantId(tenantId);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  @WithMockUser
  void getReturnsStableTriagePreviewWithNullableOwner() throws Exception {
    UUID customerId = UUID.randomUUID();
    when(authEvaluator.can(any(Authentication.class), eq("sales"), eq("read"))).thenReturn(true);
    when(accountTeamService.getAccountTeam(tenantId, customerId))
        .thenReturn(
            new CustomerAccountTeamResponse(
                customerId, null, null, OwnerResolutionReason.TRIAGE_REQUIRED, List.of()));

    mockMvc
        .perform(get("/api/v1/sales/customers/{customerId}/account-team", customerId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.customerId").value(customerId.toString()))
        .andExpect(jsonPath("$.data.defaultOwnerId").doesNotExist())
        .andExpect(jsonPath("$.data.defaultOwnerReason").value("TRIAGE_REQUIRED"));
  }

  @Test
  @WithMockUser
  void addRejectsMissingUserIdWithValidationErrorBeforeServiceInvocation() throws Exception {
    UUID customerId = UUID.randomUUID();
    when(authEvaluator.can(any(Authentication.class), eq("sales"), eq("write"))).thenReturn(true);

    mockMvc
        .perform(
            post("/api/v1/sales/customers/{customerId}/account-team", customerId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors.userId").value("User ID is required"));

    verifyNoInteractions(accountTeamService);
  }
}
