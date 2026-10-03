package com.fabricmanagement.sales.salesorder.api.controller;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContextResolver;
import com.fabricmanagement.common.infrastructure.security.PermissionEvaluator;
import com.fabricmanagement.common.infrastructure.security.SpELPermissionEvaluator;
import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.sales.salesorder.app.ApprovalAuthorityService;
import com.fabricmanagement.sales.salesorder.app.OrderPartiesService;
import com.fabricmanagement.sales.salesorder.domain.ApprovalAuthorityBasis;
import com.fabricmanagement.sales.salesorder.dto.OrderPartyDtos.GrantApprovalAuthorityRequest;
import com.fabricmanagement.sales.salesorder.dto.OrderPartyDtos.RevokeApprovalAuthorityRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * ADR-0014 OD-3c: granting and revoking approval authority needs sales:grant-approval-authority
 * through real method security; writing orders is not enough.
 */
class OrderPartiesEndpointSecurityTest {

  @Configuration
  @EnableMethodSecurity
  static class Security {
    @Bean
    PermissionEvaluator evaluator() {
      return mock(PermissionEvaluator.class);
    }

    @Bean
    AuthenticatedUserContextResolver resolver() {
      return mock(AuthenticatedUserContextResolver.class);
    }

    @Bean(name = "auth")
    SpELPermissionEvaluator auth(
        PermissionEvaluator evaluator, AuthenticatedUserContextResolver resolver) {
      return new SpELPermissionEvaluator(evaluator, resolver);
    }

    @Bean
    OrderPartiesService parties() {
      return mock(OrderPartiesService.class);
    }

    @Bean
    ApprovalAuthorityService authorities() {
      return mock(ApprovalAuthorityService.class);
    }

    @Bean
    OrderPartiesController controller(OrderPartiesService p, ApprovalAuthorityService a) {
      return new OrderPartiesController(p, a);
    }
  }

  @Test
  void grantingAndRevokingNeedTheExplicitPermission() {
    try (var context = new AnnotationConfigApplicationContext(Security.class)) {
      UUID tenant = UUID.randomUUID();
      UUID user = UUID.randomUUID();
      TenantContext.setCurrentTenantId(tenant);
      AuthenticatedUserContext principal =
          new AuthenticatedUserContext(user, "MANAGER", List.of(), null, tenant);
      Authentication auth =
          UsernamePasswordAuthenticationToken.authenticated(principal, "", List.of());
      SecurityContextHolder.getContext().setAuthentication(auth);
      when(context.getBean(AuthenticatedUserContextResolver.class).resolve(auth))
          .thenReturn(Optional.of(principal));
      PermissionEvaluator evaluator = context.getBean(PermissionEvaluator.class);
      OrderPartiesController controller = context.getBean(OrderPartiesController.class);
      UUID customer = UUID.randomUUID();
      List<Consumer<OrderPartiesController>> endpoints =
          List.of(
              c ->
                  c.grantApprovalAuthority(
                      customer,
                      new GrantApprovalAuthorityRequest(
                          "Ann Lee",
                          UUID.randomUUID(),
                          "ann.lee@example.com",
                          ApprovalAuthorityBasis.WRITTEN_MANDATE,
                          "Mandate letter",
                          LocalDate.of(2026, 10, 1),
                          null),
                      auth),
              c ->
                  c.revokeApprovalAuthority(
                      customer,
                      UUID.randomUUID(),
                      new RevokeApprovalAuthorityRequest("Left the company"),
                      auth));

      for (Map<String, Map<String, DataScope>> permissions :
          List.<Map<String, Map<String, DataScope>>>of(
              Map.of(),
              Map.of(
                  "sales",
                  Map.of(
                      "read",
                      DataScope.GLOBAL,
                      "write",
                      DataScope.GLOBAL,
                      "approve",
                      DataScope.GLOBAL)))) {
        when(evaluator.evaluate(any(), any(), any(), any()))
            .thenReturn(new PermissionResult(permissions, false));
        endpoints.forEach(
            endpoint ->
                assertThatThrownBy(() -> endpoint.accept(controller))
                    .isInstanceOf(AccessDeniedException.class));
      }
      verifyNoInteractions(context.getBean(ApprovalAuthorityService.class));

      when(evaluator.evaluate(any(), any(), any(), any()))
          .thenReturn(
              new PermissionResult(
                  Map.of("sales", Map.of("grant-approval-authority", DataScope.GLOBAL)), false));
      endpoints.forEach(
          endpoint -> assertThatCode(() -> endpoint.accept(controller)).doesNotThrowAnyException());
    } finally {
      TenantContext.clear();
      SecurityContextHolder.clearContext();
    }
  }
}
