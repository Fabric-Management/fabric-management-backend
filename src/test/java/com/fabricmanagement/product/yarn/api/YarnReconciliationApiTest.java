package com.fabricmanagement.product.yarn.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.security.SpELPermissionEvaluator;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.product.yarn.api.controller.YarnReconciliationController;
import com.fabricmanagement.product.yarn.app.backfill.YarnReadinessService;
import com.fabricmanagement.product.yarn.app.backfill.YarnReconciliationService;
import com.fabricmanagement.product.yarn.domain.backfill.YarnBackfillQueueStatus;
import com.fabricmanagement.product.yarn.domain.exception.YarnDomainException;
import com.fabricmanagement.product.yarn.domain.exception.YarnReconciliationException;
import com.fabricmanagement.product.yarn.dto.YarnReadinessDto;
import com.fabricmanagement.product.yarn.dto.YarnUnlinkedOpenDocumentsDto;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

@WebMvcTest(YarnReconciliationController.class)
@EnableMethodSecurity
class YarnReconciliationApiTest {

  private static final String READ = "@auth.can(authentication, 'yarn', 'read')";
  private static final String WRITE = "@auth.can(authentication, 'yarn', 'write')";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private YarnReconciliationService reconciliationService;
  @MockitoBean private YarnReadinessService readinessService;
  @MockitoBean private com.fabricmanagement.platform.auth.app.JwtService jwtService;

  @MockitoBean
  private com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort tenantQueryPort;

  @MockitoBean(name = "auth")
  private SpELPermissionEvaluator authEvaluator;

  @Test
  void everyNewHandlerUsesOnlyTheExistingYarnReadOrWritePermission() {
    assertThat(Arrays.stream(YarnReconciliationController.class.getDeclaredMethods()))
        .filteredOn(
            method ->
                method.isAnnotationPresent(GetMapping.class)
                    || method.isAnnotationPresent(PostMapping.class))
        .allSatisfy(
            method -> {
              PreAuthorize guard = method.getAnnotation(PreAuthorize.class);
              assertThat(guard).as(method.getName()).isNotNull();
              assertThat(guard.value())
                  .isEqualTo(method.isAnnotationPresent(GetMapping.class) ? READ : WRITE);
            });
  }

  @Test
  @WithMockUser
  void listUsesReadPermissionAndIgnoresCallerSort() throws Exception {
    when(authEvaluator.can(any(Authentication.class), eq("yarn"), eq("read"))).thenReturn(true);
    when(reconciliationService.list(eq(YarnBackfillQueueStatus.OPEN), any(Pageable.class)))
        .thenReturn(Page.empty());

    mockMvc
        .perform(
            get("/api/v1/production/yarns/reconciliations").queryParam("sort", "createdAt,desc"))
        .andExpect(status().isOk());

    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(reconciliationService).list(eq(YarnBackfillQueueStatus.OPEN), pageable.capture());
    assertThat(pageable.getValue().getSort().isUnsorted()).isTrue();
  }

  @Test
  @WithMockUser
  void beanAndParameterValidationUseThe422Contracts() throws Exception {
    when(authEvaluator.can(any(Authentication.class), eq("yarn"), eq("read"))).thenReturn(true);
    when(authEvaluator.can(any(Authentication.class), eq("yarn"), eq("write"))).thenReturn(true);
    UUID id = UUID.randomUUID();

    mockMvc
        .perform(
            post("/api/v1/production/yarns/reconciliations/{id}/choose", id)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceRecordId\":\" \"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    mockMvc
        .perform(get("/api/v1/production/yarns/readiness").queryParam("blockersLimit", "0"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("CONSTRAINT_VIOLATION"));
    mockMvc
        .perform(get("/api/v1/production/yarns/readiness").queryParam("blockersLimit", "201"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("CONSTRAINT_VIOLATION"));
    for (String endpoint :
        java.util.List.of(
            "/api/v1/production/yarns/reconciliations",
            "/api/v1/production/yarns/reconciliations/" + id + "/candidates")) {
      mockMvc
          .perform(get(endpoint).queryParam("size", "0"))
          .andExpect(status().isUnprocessableEntity())
          .andExpect(jsonPath("$.code").value("CONSTRAINT_VIOLATION"));
      mockMvc
          .perform(get(endpoint).queryParam("size", "201"))
          .andExpect(status().isUnprocessableEntity())
          .andExpect(jsonPath("$.code").value("CONSTRAINT_VIOLATION"));
      mockMvc
          .perform(get(endpoint).queryParam("page", "-1"))
          .andExpect(status().isUnprocessableEntity())
          .andExpect(jsonPath("$.code").value("CONSTRAINT_VIOLATION"));
    }
    mockMvc
        .perform(
            get("/api/v1/production/yarns/reconciliations")
                .queryParam("page", "-1")
                .queryParam("size", "201"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("CONSTRAINT_VIOLATION"));
  }

  @Test
  @WithMockUser
  void onlyUnreadableJsonReturns400() throws Exception {
    when(authEvaluator.can(any(Authentication.class), eq("yarn"), eq("write"))).thenReturn(true);

    mockMvc
        .perform(
            post("/api/v1/production/yarns/reconciliations/{id}/choose", UUID.randomUUID())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not-json"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
  }

  @Test
  @WithMockUser
  void readinessAlwaysSerializesBothZeroValuedUnlinkedDocumentCounts() throws Exception {
    when(authEvaluator.can(any(Authentication.class), eq("yarn"), eq("read"))).thenReturn(true);
    when(readinessService.readiness(50))
        .thenReturn(
            new YarnReadinessDto(
                true, 0, 0, 0, new YarnUnlinkedOpenDocumentsDto(0, 0), 0, 0, 90, List.of()));

    mockMvc
        .perform(get("/api/v1/production/yarns/readiness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.unlinkedOpenYarnDocuments.openPurchaseOrders").value(0))
        .andExpect(jsonPath("$.data.unlinkedOpenYarnDocuments.openWorkOrders").value(0));
  }

  @Test
  @WithMockUser
  void writeEndpointsAreForbiddenWithoutYarnWrite() throws Exception {
    when(authEvaluator.can(any(Authentication.class), eq("yarn"), eq("write"))).thenReturn(false);

    mockMvc
        .perform(
            post("/api/v1/production/yarns/reconciliations/{id}/dismiss", UUID.randomUUID())
                .with(csrf()))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser
  void unknownAndConflictCodesKeepTheirExactHttpSurface() throws Exception {
    when(authEvaluator.can(any(Authentication.class), eq("yarn"), eq("write"))).thenReturn(true);
    UUID id = UUID.randomUUID();
    doThrow(new NotFoundException("missing")).when(reconciliationService).dismiss(id);
    mockMvc
        .perform(post("/api/v1/production/yarns/reconciliations/{id}/dismiss", id).with(csrf()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));

    doThrow(
            new YarnReconciliationException("not open", "YARN_RECONCILIATION_NOT_OPEN"),
            new YarnReconciliationException("not found", "YARN_RECONCILIATION_CANDIDATE_NOT_FOUND"),
            new YarnReconciliationException(
                "overlength", "YARN_RECONCILIATION_CANDIDATE_OVERLENGTH"),
            new YarnDomainException("I17", "obsolete"))
        .when(reconciliationService)
        .choose(eq(id), any());
    for (String code :
        java.util.List.of(
            "YARN_RECONCILIATION_NOT_OPEN",
            "YARN_RECONCILIATION_CANDIDATE_NOT_FOUND",
            "YARN_RECONCILIATION_CANDIDATE_OVERLENGTH",
            "YARN_INVARIANT_VIOLATION")) {
      mockMvc
          .perform(
              post("/api/v1/production/yarns/reconciliations/{id}/choose", id)
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"sourceKind\":\"BATCH_ACTUAL\",\"sourceRecordId\":\"legacy-1\"}"))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value(code));
    }
  }
}
