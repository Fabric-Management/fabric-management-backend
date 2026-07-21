package com.fabricmanagement.production.quality.decision.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.SpELPermissionEvaluator;
import com.fabricmanagement.production.quality.decision.app.QualityDecisionQueryService;
import com.fabricmanagement.production.quality.decision.app.QualityDecisionService;
import com.fabricmanagement.production.quality.decision.domain.QualityDecision;
import com.fabricmanagement.production.quality.decision.domain.QualityDecisionOrigin;
import com.fabricmanagement.production.quality.decision.domain.QualityDecisionOutcome;
import com.fabricmanagement.production.quality.decision.domain.QualityDecisionScope;
import com.fabricmanagement.production.quality.decision.dto.QualityDecisionDto;
import com.fabricmanagement.production.quality.decision.mapper.QualityDecisionMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(QualityDecisionController.class)
@EnableMethodSecurity
class QualityDecisionControllerSecurityTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID ACTOR_ID = UUID.randomUUID();
  private static final UUID BATCH_ID = UUID.randomUUID();

  @Autowired private MockMvc mockMvc;

  @MockBean private QualityDecisionService decisionService;
  @MockBean private QualityDecisionQueryService queryService;
  @MockBean private QualityDecisionMapper mapper;
  @MockBean private com.fabricmanagement.platform.auth.app.JwtService jwtService;

  @MockBean
  private com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort tenantQueryPort;

  @MockBean(name = "auth")
  private SpELPermissionEvaluator authEvaluator;

  @BeforeEach
  void setUpTenantContext() {
    TenantContext.setCurrentTenantId(TENANT_ID);
    TenantContext.setCurrentUserId(ACTOR_ID);
  }

  @AfterEach
  void clearTenantContext() {
    TenantContext.clear();
  }

  @Test
  void queueRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/v1/production/quality/queue")).andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser
  void workerWithoutApproveCannotRecordDecision() throws Exception {
    when(authEvaluator.can(any(Authentication.class), eq("quality"), eq("approve")))
        .thenReturn(false);

    mockMvc
        .perform(
            post("/api/v1/production/quality/batches/{batchId}/decisions", BATCH_ID)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"scope":"FULL_LOT","outcome":"RELEASED"}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser
  void supervisorWithApproveCanRecordDecision() throws Exception {
    when(authEvaluator.can(any(Authentication.class), eq("quality"), eq("approve")))
        .thenReturn(true);
    QualityDecision decision =
        QualityDecision.create(
            TENANT_ID,
            BATCH_ID,
            QualityDecisionScope.FULL_LOT,
            QualityDecisionOutcome.RELEASED,
            null,
            null,
            ACTOR_ID,
            QualityDecisionOrigin.MANUAL,
            null,
            null,
            1,
            Instant.now());
    QualityDecisionDto dto =
        new QualityDecisionDto(
            decision.getId(),
            BATCH_ID,
            QualityDecisionScope.FULL_LOT,
            QualityDecisionOutcome.RELEASED,
            null,
            null,
            ACTOR_ID,
            QualityDecisionOrigin.MANUAL,
            null,
            null,
            decision.getDecidedAt(),
            1);
    when(decisionService.recordDecision(any(), any())).thenReturn(decision);
    when(mapper.toDto(decision)).thenReturn(dto);

    mockMvc
        .perform(
            post("/api/v1/production/quality/batches/{batchId}/decisions", BATCH_ID)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"scope":"FULL_LOT","outcome":"RELEASED"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.actorId").value(ACTOR_ID.toString()))
        .andExpect(jsonPath("$.data.origin").value("MANUAL"));
  }

  @Test
  @WithMockUser
  void qualityReadAllowsPendingQueue() throws Exception {
    when(authEvaluator.can(any(Authentication.class), eq("quality"), eq("read"))).thenReturn(true);
    when(queryService.getQueue(any())).thenReturn(Page.empty());

    mockMvc
        .perform(get("/api/v1/production/quality/queue"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content").isArray());
  }
}
