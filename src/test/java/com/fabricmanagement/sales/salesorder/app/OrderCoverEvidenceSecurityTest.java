package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.SpELPermissionEvaluator;
import com.fabricmanagement.sales.salesorder.domain.*;
import com.fabricmanagement.sales.salesorder.domain.port.OrderCoverEvidencePort;
import com.fabricmanagement.sales.salesorder.infra.repository.*;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.SimpleTransactionStatus;

@SpringJUnitConfig(OrderCoverEvidenceSecurityTest.Config.class)
class OrderCoverEvidenceSecurityTest {
  @Configuration
  @EnableMethodSecurity
  @EnableTransactionManagement
  static class Config {
    @Bean
    SpELPermissionEvaluator auth() {
      return mock(SpELPermissionEvaluator.class);
    }

    @Bean
    OrderCoverEvidenceRepository repository() {
      return mock(OrderCoverEvidenceRepository.class);
    }

    @Bean
    SalesOrderRepository orders() {
      return mock(SalesOrderRepository.class);
    }

    @Bean
    SalesOrderLineRepository lines() {
      return mock(SalesOrderLineRepository.class);
    }

    @Bean
    OrderCoverEvidenceStreamRepository streams() {
      return mock(OrderCoverEvidenceStreamRepository.class);
    }

    @Bean
    OrderCoverEvidencePort production() {
      return mock(OrderCoverEvidencePort.class);
    }

    @Bean
    PlatformTransactionManager transactionManager() {
      return mock(PlatformTransactionManager.class);
    }

    @Bean
    OrderCoverEvidenceService service(
        OrderCoverEvidenceRepository repository,
        SalesOrderRepository orders,
        SalesOrderLineRepository lines,
        OrderCoverEvidenceStreamRepository streams,
        OrderCoverEvidencePort production,
        PlatformTransactionManager transactionManager) {
      return new OrderCoverEvidenceService(
          orders, lines, streams, repository, production, Clock.systemUTC(), transactionManager);
    }

    @Bean
    OrderCoverEvidenceRebuildService rebuilds(
        OrderCoverEvidenceStreamRepository streams, OrderCoverEvidenceService evidence) {
      return new OrderCoverEvidenceRebuildService(streams, evidence);
    }
  }

  @Autowired OrderCoverEvidenceService service;
  @Autowired SpELPermissionEvaluator auth;
  @Autowired OrderCoverEvidenceRepository repository;
  @Autowired SalesOrderRepository orders;
  @Autowired OrderCoverEvidenceStreamRepository streams;
  @Autowired OrderCoverEvidencePort production;
  @Autowired OrderCoverEvidenceRebuildService rebuilds;
  @Autowired PlatformTransactionManager transactionManager;

  @BeforeEach
  void identity() {
    reset(auth, repository, orders, streams, production, transactionManager);
    when(transactionManager.getTransaction(any()))
        .thenAnswer(call -> new SimpleTransactionStatus());
    TenantContext.setCurrentTenantId(UUID.randomUUID());
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated("operator", "unused", List.of()));
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
    SecurityContextHolder.clearContext();
  }

  @Test
  void missingGrantDeniesBeforeLookup() {
    assertThatThrownBy(() -> service.read(UUID.randomUUID(), UUID.randomUUID()))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(repository);
  }

  @Test
  void narrowScopeIsNotExpandedToTenantWideRead() {
    when(auth.can(any(), anyString(), anyString())).thenReturn(true);
    when(auth.hasScope(any(), eq("sales"), eq("read"), eq("ORGANIZATION"))).thenReturn(false);
    assertThatThrownBy(() -> service.read(UUID.randomUUID(), UUID.randomUUID()))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(repository);
  }

  @Test
  void entitledOperatorStillUsesTenantAndOrderInHistoricalLookup() {
    when(auth.can(any(), anyString(), anyString())).thenReturn(true);
    when(auth.hasScope(any(), eq("sales"), eq("read"), eq("ORGANIZATION"))).thenReturn(true);
    UUID order = UUID.randomUUID();
    UUID evidence = UUID.randomUUID();
    when(repository.findByTenantIdAndSalesOrderIdAndId(
            TenantContext.requireTenantId(), order, evidence))
        .thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.read(order, evidence))
        .isInstanceOf(com.fabricmanagement.sales.common.exception.OrderDomainException.class);
    verify(repository)
        .findByTenantIdAndSalesOrderIdAndId(TenantContext.requireTenantId(), order, evidence);
  }

  @Test
  void allMutationEntrypointsRejectMissingGrantsBeforeAnyQuery() {
    UUID order = UUID.randomUUID();
    UUID id = UUID.randomUUID();
    assertThatThrownBy(() -> service.refresh(order, id)).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.rebuild(order, id)).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.revalidate(order, id))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> rebuilds.rebuildOrder(order))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> rebuilds.rebuildTenantPage(0))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(repository, orders, streams, production, transactionManager);
  }

  @Test
  void readEntitlementDoesNotPermitWritesOrTenantWideRebuild() {
    when(auth.can(any(), anyString(), eq("read"))).thenReturn(true);
    when(auth.hasScope(any(), eq("sales"), eq("read"), eq("ORGANIZATION"))).thenReturn(true);
    UUID order = UUID.randomUUID();
    UUID id = UUID.randomUUID();
    assertThatThrownBy(() -> service.rebuild(order, id)).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.revalidate(order, id))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> rebuilds.rebuildOrder(order))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> rebuilds.rebuildTenantPage(0))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(repository, orders, streams, production);
  }

  @Test
  void narrowWriteScopeCannotRebuildOrRevalidate() {
    when(auth.can(any(), anyString(), anyString())).thenReturn(true);
    UUID order = UUID.randomUUID();
    assertThatThrownBy(() -> service.rebuild(order, UUID.randomUUID()))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.revalidate(order, UUID.randomUUID()))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> rebuilds.rebuildOrder(order))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> rebuilds.rebuildTenantPage(0))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(repository, orders, streams, production);
  }

  @Test
  void entitledWriterTraversesRealInterceptorsAndAppendsEvidence() {
    when(auth.can(any(), anyString(), eq("write"))).thenReturn(true);
    when(auth.hasScope(any(), eq("sales"), eq("write"), eq("ORGANIZATION"))).thenReturn(true);
    UUID tenant = TenantContext.requireTenantId();
    UUID order = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();
    var scope = OrderCoverEvidenceStream.create(tenant, order, caseId);
    when(streams.findByTenantIdAndSalesOrderIdOrderByCaseId(tenant, order))
        .thenReturn(List.of(scope));
    when(streams.lockScope(tenant, caseId)).thenReturn(Optional.of(scope));
    when(orders.findByTenantIdAndId(tenant, order))
        .thenReturn(Optional.of(SalesOrder.builder().build()));
    when(production.inspect(any()))
        .thenReturn(new OrderCoverEvidencePort.Inputs(List.of(), List.of()));
    when(repository.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              OrderCoverEvidence snapshot = call.getArgument(0);
              snapshot.setId(UUID.randomUUID());
              return snapshot;
            });
    assertThat(rebuilds.rebuildOrder(order)).isEqualTo(1);
    verify(repository).saveAndFlush(any());
    verify(transactionManager).getTransaction(argThat(definition -> definition.isReadOnly()));
    verify(transactionManager)
        .getTransaction(
            argThat(
                definition ->
                    definition.getPropagationBehavior()
                        == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
    when(streams.findByTenantId(eq(tenant), any())).thenReturn(Page.empty());
    assertThat(rebuilds.rebuildTenantPage(0).rebuilt()).isZero();
    verify(streams).findByTenantId(eq(tenant), argThat(page -> page.getPageSize() == 50));
  }
}
