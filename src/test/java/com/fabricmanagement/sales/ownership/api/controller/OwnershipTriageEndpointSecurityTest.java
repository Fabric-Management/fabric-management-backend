package com.fabricmanagement.sales.ownership.api.controller;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContextResolver;
import com.fabricmanagement.common.infrastructure.security.PermissionEvaluator;
import com.fabricmanagement.common.infrastructure.security.SpELPermissionEvaluator;
import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.sales.ownership.app.CustomerAccountTeamService;
import com.fabricmanagement.sales.ownership.app.CustomerCommercialAssignmentService;
import com.fabricmanagement.sales.ownership.app.OwnershipTriageService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;

class OwnershipTriageEndpointSecurityTest {

  @Configuration
  @EnableMethodSecurity
  static class SecurityConfiguration {

    @Bean
    PermissionEvaluator permissionEvaluator() {
      return mock(PermissionEvaluator.class);
    }

    @Bean
    AuthenticatedUserContextResolver authenticatedUserContextResolver() {
      return mock(AuthenticatedUserContextResolver.class);
    }

    @Bean(name = "auth")
    SpELPermissionEvaluator auth(
        PermissionEvaluator evaluator, AuthenticatedUserContextResolver resolver) {
      return new SpELPermissionEvaluator(evaluator, resolver);
    }

    @Bean
    OwnershipTriageService ownershipTriageService() {
      return mock(OwnershipTriageService.class);
    }

    @Bean
    CustomerCommercialAssignmentService assignmentService() {
      return mock(CustomerCommercialAssignmentService.class);
    }

    @Bean
    CustomerAccountTeamService accountTeamService() {
      return mock(CustomerAccountTeamService.class);
    }

    @Bean
    OwnershipTriageController ownershipTriageController(
        OwnershipTriageService triageService,
        CustomerCommercialAssignmentService assignmentService) {
      return new OwnershipTriageController(triageService, assignmentService);
    }

    @Bean
    CustomerAccountTeamController customerAccountTeamController(
        CustomerAccountTeamService accountTeamService) {
      return new CustomerAccountTeamController(accountTeamService);
    }
  }

  @Test
  void triageReadRequiresAssignOwnerAndCandidatesStillRequireWrite() {
    try (var context = new AnnotationConfigApplicationContext(SecurityConfiguration.class)) {
      UUID tenantId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      UUID customerId = UUID.randomUUID();
      TenantContext.setCurrentTenantId(tenantId);
      AuthenticatedUserContext userContext =
          new AuthenticatedUserContext(userId, "WORKER", List.of("SALES"), null, tenantId);
      var authentication =
          UsernamePasswordAuthenticationToken.authenticated(userContext, "n/a", List.of());
      SecurityContextHolder.getContext().setAuthentication(authentication);
      when(context.getBean(AuthenticatedUserContextResolver.class).resolve(authentication))
          .thenReturn(Optional.of(userContext));
      OwnershipTriageService triageService = context.getBean(OwnershipTriageService.class);
      CustomerAccountTeamService accountTeamService =
          context.getBean(CustomerAccountTeamService.class);
      when(triageService.list(any(), any())).thenReturn(Page.empty());
      when(accountTeamService.listCandidates(tenantId, customerId)).thenReturn(List.of());

      PermissionEvaluator evaluator = context.getBean(PermissionEvaluator.class);
      OwnershipTriageController triage = context.getBean(OwnershipTriageController.class);
      CustomerAccountTeamController accountTeam =
          context.getBean(CustomerAccountTeamController.class);

      grant(evaluator, "read");
      assertThatThrownBy(() -> triage.list(PageRequest.of(0, 20)))
          .isInstanceOf(AccessDeniedException.class);
      assertThatThrownBy(() -> accountTeam.listCandidates(customerId))
          .isInstanceOf(AccessDeniedException.class);

      grant(evaluator, "assign-owner");
      assertThatCode(() -> triage.list(PageRequest.of(0, 20))).doesNotThrowAnyException();
      assertThatThrownBy(() -> accountTeam.listCandidates(customerId))
          .isInstanceOf(AccessDeniedException.class);

      grant(evaluator, "write");
      assertThatCode(() -> accountTeam.listCandidates(customerId)).doesNotThrowAnyException();
    } finally {
      TenantContext.clear();
      SecurityContextHolder.clearContext();
    }
  }

  private void grant(PermissionEvaluator evaluator, String action) {
    when(evaluator.evaluate(any(), any(), any(), any()))
        .thenReturn(
            new PermissionResult(Map.of("sales", Map.of(action, DataScope.ORGANIZATION)), false));
  }
}
