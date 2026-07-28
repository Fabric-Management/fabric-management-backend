package com.fabricmanagement.sales.ownership.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.SpELPermissionEvaluator;
import com.fabricmanagement.sales.ownership.app.CustomerCommercialAssignmentService;
import com.fabricmanagement.sales.ownership.app.OwnershipTriageService;
import com.fabricmanagement.sales.ownership.domain.ActorRef;
import com.fabricmanagement.sales.ownership.domain.AssignmentSource;
import com.fabricmanagement.sales.ownership.domain.CustomerCommercialAssignment;
import com.fabricmanagement.sales.ownership.dto.AssignPrimaryRepresentativeRequest;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = {CustomerCommercialAssignmentController.class, OwnershipTriageController.class})
@EnableMethodSecurity
class OwnershipAssignmentControllerSecurityTest {

  private static final String ASSIGN_OWNER_EXPRESSION =
      "@auth.can(authentication, 'sales', 'assign-owner')";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private CustomerCommercialAssignmentService assignmentService;
  @MockitoBean private OwnershipTriageService triageService;
  @MockitoBean private com.fabricmanagement.platform.auth.app.JwtService jwtService;

  @MockitoBean
  private com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort tenantQueryPort;

  @MockitoBean(name = "auth")
  private SpELPermissionEvaluator authEvaluator;

  @AfterEach
  void clearTenantContext() {
    TenantContext.clear();
  }

  @Test
  @WithMockUser(roles = "MANAGER")
  void salesManagerWithAssignOwnerCanUseBothEndpoints() throws Exception {
    when(authEvaluator.can(any(Authentication.class), eq("sales"), eq("assign-owner")))
        .thenReturn(true);

    assertBothEndpointsReturnCreated();
  }

  @Test
  @WithMockUser(roles = "MANAGER")
  void managerRoleWithoutAssignOwnerIsForbiddenOnBothEndpoints() throws Exception {
    when(authEvaluator.can(any(Authentication.class), eq("sales"), eq("assign-owner")))
        .thenReturn(false);

    assertBothEndpointsReturnForbidden();
  }

  @Test
  @WithMockUser(roles = "SALES_COORDINATOR")
  void nonManagerWithUserOverrideCanUseBothEndpoints() throws Exception {
    when(authEvaluator.can(any(Authentication.class), eq("sales"), eq("assign-owner")))
        .thenReturn(true);

    assertBothEndpointsReturnCreated();
  }

  @Test
  @WithMockUser(roles = "WORKER")
  void salesWriteWithoutAssignOwnerIsForbiddenOnBothEndpoints() throws Exception {
    when(authEvaluator.can(any(Authentication.class), eq("sales"), eq("assign-owner")))
        .thenReturn(false);

    assertBothEndpointsReturnForbidden();
    verify(authEvaluator, never()).can(any(Authentication.class), eq("sales"), eq("write"));
  }

  @Test
  void bothControllersUseOnlyTheAssignOwnerCapability() throws NoSuchMethodException {
    Method assignPrimary =
        CustomerCommercialAssignmentController.class.getMethod(
            "assignPrimary", UUID.class, AssignPrimaryRepresentativeRequest.class);
    Method resolve =
        OwnershipTriageController.class.getMethod(
            "resolve", UUID.class, AssignPrimaryRepresentativeRequest.class);

    assertThat(assignPrimary.getAnnotation(PreAuthorize.class).value())
        .isEqualTo(ASSIGN_OWNER_EXPRESSION);
    assertThat(resolve.getAnnotation(PreAuthorize.class).value())
        .isEqualTo(ASSIGN_OWNER_EXPRESSION);
  }

  private void assertBothEndpointsReturnCreated() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    UUID representativeId = UUID.randomUUID();
    CustomerCommercialAssignment assignment =
        CustomerCommercialAssignment.open(
            customerId,
            representativeId,
            Instant.parse("2026-07-28T12:00:00Z"),
            AssignmentSource.MANUAL,
            ActorRef.user(actorId),
            "OWNERSHIP_POLICY_V1",
            null);
    assignment.setId(UUID.randomUUID());
    assignment.setTenantId(tenantId);
    when(assignmentService.assignPrimary(
            eq(tenantId),
            eq(customerId),
            eq(representativeId),
            any(AssignmentSource.class),
            eq(ActorRef.user(actorId))))
        .thenReturn(assignment);

    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(actorId);
    mockMvc
        .perform(
            post("/api/v1/sales/customers/{customerId}/commercial-assignments", customerId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(representativeId)))
        .andExpect(status().isCreated());

    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(actorId);
    mockMvc
        .perform(
            post("/api/v1/sales/ownership/triage/{customerId}/resolve", customerId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(representativeId)))
        .andExpect(status().isCreated());
  }

  private void assertBothEndpointsReturnForbidden() throws Exception {
    UUID customerId = UUID.randomUUID();
    String body = requestBody(UUID.randomUUID());

    mockMvc
        .perform(
            post("/api/v1/sales/customers/{customerId}/commercial-assignments", customerId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/api/v1/sales/ownership/triage/{customerId}/resolve", customerId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
  }

  private String requestBody(UUID representativeId) {
    return """
        {"representativeId":"%s"}
        """
        .formatted(representativeId);
  }
}
