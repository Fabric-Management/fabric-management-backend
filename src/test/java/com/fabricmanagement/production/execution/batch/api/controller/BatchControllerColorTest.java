package com.fabricmanagement.production.execution.batch.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.security.SpELPermissionEvaluator;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.production.execution.batch.app.BatchAttributeService;
import com.fabricmanagement.production.execution.batch.app.BatchCertificationService;
import com.fabricmanagement.production.execution.batch.app.BatchOperationsService;
import com.fabricmanagement.production.execution.batch.app.BatchService;
import com.fabricmanagement.production.execution.batch.domain.exception.BatchDomainException;
import com.fabricmanagement.production.execution.batch.dto.AddBatchAttributeRequest;
import com.fabricmanagement.production.execution.batch.dto.BatchDto;
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

@WebMvcTest(BatchController.class)
@EnableMethodSecurity
class BatchControllerColorTest {

  private static final UUID BATCH_ID = UUID.randomUUID();
  private static final UUID COLOR_ID = UUID.randomUUID();

  @Autowired private MockMvc mockMvc;

  @MockBean private BatchService batchService;
  @MockBean private BatchOperationsService batchOperationsService;
  @MockBean private BatchCertificationService batchCertificationService;
  @MockBean private BatchAttributeService batchAttributeService;
  @MockBean private com.fabricmanagement.platform.auth.app.JwtService jwtService;

  @MockBean
  private com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort tenantQueryPort;

  @MockBean(name = "auth")
  private SpELPermissionEvaluator authEvaluator;

  @Test
  @WithMockUser
  void patchAssignsAndClearsColorBehindProductsWritePermission() throws Exception {
    allowWrite(true);
    when(batchService.updateColor(BATCH_ID, COLOR_ID))
        .thenReturn(BatchDto.builder().id(BATCH_ID).colorId(COLOR_ID).build());
    when(batchService.updateColor(BATCH_ID, null))
        .thenReturn(BatchDto.builder().id(BATCH_ID).colorId(null).build());

    mockMvc
        .perform(
            patch("/api/v1/production/batches/{id}/color", BATCH_ID)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"colorId\":\"" + COLOR_ID + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.colorId").value(COLOR_ID.toString()));

    mockMvc
        .perform(
            patch("/api/v1/production/batches/{id}/color", BATCH_ID)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"colorId\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.colorId").isEmpty());
  }

  @Test
  @WithMockUser
  void patchRejectsReadOnlyCaller() throws Exception {
    allowWrite(false);

    mockMvc
        .perform(
            patch("/api/v1/production/batches/{id}/color", BATCH_ID)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"colorId\":null}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser
  void patchMapsUnknownInactiveOrCrossTenantColorToNotFound() throws Exception {
    allowWrite(true);
    when(batchService.updateColor(BATCH_ID, COLOR_ID))
        .thenThrow(new NotFoundException("Active color not found: " + COLOR_ID));

    mockMvc
        .perform(
            patch("/api/v1/production/batches/{id}/color", BATCH_ID)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"colorId\":\"" + COLOR_ID + "\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @WithMockUser
  void legacyColorGuardCodeSurfacesThroughCanonicalExceptionHandler() throws Exception {
    allowWrite(true);
    when(batchAttributeService.add(eq(BATCH_ID), any(AddBatchAttributeRequest.class)))
        .thenThrow(
            new BatchDomainException(
                "Legacy color attributes are read-only after the batch color cutover",
                "LEGACY_COLOR_ATTRIBUTE_WRITE",
                409));

    mockMvc
        .perform(
            post("/api/v1/production/batches/{id}/attributes", BATCH_ID)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"attributeId\":\"" + UUID.randomUUID() + "\",\"value\":\"x\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("LEGACY_COLOR_ATTRIBUTE_WRITE"));
  }

  private void allowWrite(boolean allowed) {
    when(authEvaluator.can(any(Authentication.class), eq("products"), eq("write")))
        .thenReturn(allowed);
  }
}
