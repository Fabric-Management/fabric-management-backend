package com.fabricmanagement.flowboard.routing.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.*;
import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.flowboard.routing.api.controller.RoutingController;
import com.fabricmanagement.flowboard.routing.domain.RoutingPoolKey;
import com.fabricmanagement.flowboard.routing.dto.*;
import com.fabricmanagement.platform.user.domain.DataScope;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;

class RoutingEndpointSecurityTest {
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
    RoutingQueryService queries() {
      return mock(RoutingQueryService.class);
    }

    @Bean
    RoutingPoolConfigurationService configuration() {
      return mock(RoutingPoolConfigurationService.class);
    }

    @Bean
    RoutingRepairService repair() {
      return mock(RoutingRepairService.class);
    }

    @Bean
    RoutingController controller(
        RoutingQueryService q, RoutingPoolConfigurationService c, RoutingRepairService r) {
      return new RoutingController(q, c, r);
    }
  }

  @Test
  void allSixEndpointsRequireManageRoutingThroughRealMethodSecurity() {
    try (var context = new AnnotationConfigApplicationContext(Security.class)) {
      UUID tenant = UUID.randomUUID(), user = UUID.randomUUID();
      TenantContext.setCurrentTenantId(tenant);
      var auth = UsernamePasswordAuthenticationToken.authenticated("actor", "", List.of());
      SecurityContextHolder.getContext().setAuthentication(auth);
      when(context.getBean(AuthenticatedUserContextResolver.class).resolve(auth))
          .thenReturn(
              Optional.of(new AuthenticatedUserContext(user, "WORKER", List.of(), null, tenant)));
      var evaluator = context.getBean(PermissionEvaluator.class);
      var controller = context.getBean(RoutingController.class);
      List<Consumer<RoutingController>> endpoints =
          List.of(
              c -> c.pool(RoutingPoolKey.ORDER_COVER),
              c ->
                  c.configure(
                      RoutingPoolKey.ORDER_COVER, new RoutingPoolUpdateRequest(null, Set.of())),
              c ->
                  c.candidates(
                      RoutingPoolKey.ORDER_COVER,
                      null,
                      org.springframework.data.domain.PageRequest.of(0, 10)),
              c -> c.eligibility(UUID.randomUUID()),
              c -> c.failures(null, null, org.springframework.data.domain.PageRequest.of(0, 10)),
              c -> c.repair(RoutingPoolKey.ORDER_COVER));
      for (var permissions :
          List.of(
              Map.<String, Map<String, DataScope>>of(),
              Map.of("flowboard", Map.of("write", DataScope.GLOBAL)))) {
        when(evaluator.evaluate(any(), any(), any(), any()))
            .thenReturn(new PermissionResult(permissions, false));
        endpoints.forEach(
            endpoint ->
                assertThatThrownBy(() -> endpoint.accept(controller))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class));
      }
      verifyNoInteractions(
          context.getBean(RoutingQueryService.class),
          context.getBean(RoutingPoolConfigurationService.class),
          context.getBean(RoutingRepairService.class));
      when(evaluator.evaluate(any(), any(), any(), any()))
          .thenReturn(
              new PermissionResult(
                  Map.of("flowboard", Map.of("manage-routing", DataScope.GLOBAL)), false));
      var queries = context.getBean(RoutingQueryService.class);
      when(queries.candidates(any(), any(), any(), any()))
          .thenReturn(org.springframework.data.domain.Page.empty());
      when(queries.failures(any(), any(), any(), any()))
          .thenReturn(org.springframework.data.domain.Page.empty());
      when(context
              .getBean(RoutingRepairService.class)
              .repair(any(), any(), org.mockito.ArgumentMatchers.anyBoolean()))
          .thenReturn(
              new com.fabricmanagement.flowboard.routing.domain.RoutingRecords.Repair(
                  0, 0, 0, 0, true));
      endpoints.forEach(
          endpoint -> assertThatCode(() -> endpoint.accept(controller)).doesNotThrowAnyException());
    } finally {
      TenantContext.clear();
      SecurityContextHolder.clearContext();
    }
  }
}
