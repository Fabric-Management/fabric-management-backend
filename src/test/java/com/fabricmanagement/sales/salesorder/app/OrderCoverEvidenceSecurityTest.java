package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.sales.salesorder.domain.OrderCoverEvidenceStream;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderCoverEvidenceStreamRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class OrderCoverEvidenceSecurityTest {
  private final UUID tenantId = UUID.randomUUID();
  private final UUID actorId = UUID.randomUUID();
  private final UUID orderId = UUID.randomUUID();
  private final SalesOrderRepository orders = mock(SalesOrderRepository.class);
  private final SalesOrderAccessPolicy policy = mock(SalesOrderAccessPolicy.class);
  private final OrderCoverObjectAccess access = new OrderCoverObjectAccess(orders, policy);

  @BeforeEach
  void setContext() {
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(actorId);
  }

  @AfterEach
  void clearContext() {
    TenantContext.clear();
  }

  @Test
  void inaccessibleOrderIsIndistinguishableFromMissingOrder() {
    SalesOrder order = mock(SalesOrder.class);
    when(orders.findByTenantIdAndId(tenantId, orderId)).thenReturn(Optional.of(order));
    when(policy.canRead(tenantId, actorId, order)).thenReturn(false);

    assertThatThrownBy(() -> access.readable(orderId, actorId))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("Sales order not found");
  }

  @Test
  void writeCheckUsesFreshObjectScopeAfterReadabilityIsEstablished() {
    SalesOrder order = mock(SalesOrder.class);
    when(orders.findByTenantIdAndId(tenantId, orderId)).thenReturn(Optional.of(order));
    when(policy.canRead(tenantId, actorId, order)).thenReturn(true);
    when(policy.canWrite(
            tenantId, actorId, order, SalesOrderAccessPolicy.PermissionFreshness.FRESH))
        .thenReturn(false);

    assertThatThrownBy(() -> access.assertWritable(orderId, actorId))
        .isInstanceOf(AccessDeniedException.class);

    verify(policy)
        .canWrite(tenantId, actorId, order, SalesOrderAccessPolicy.PermissionFreshness.FRESH);
  }

  @Test
  void orderRebuildAppliesObjectWriteScopeBeforeAppendingEvidence() {
    OrderCoverEvidenceStreamRepository streams = mock(OrderCoverEvidenceStreamRepository.class);
    OrderCoverEvidenceService evidence = mock(OrderCoverEvidenceService.class);
    OrderCoverObjectAccess guardedAccess = mock(OrderCoverObjectAccess.class);
    OrderCoverEvidenceRebuildService rebuilds =
        new OrderCoverEvidenceRebuildService(streams, evidence, guardedAccess);
    UUID caseId = UUID.randomUUID();
    OrderCoverEvidenceStream stream = OrderCoverEvidenceStream.create(tenantId, orderId, caseId);
    when(streams.findByTenantIdAndSalesOrderIdOrderByCaseId(tenantId, orderId))
        .thenReturn(List.of(stream));

    assertThat(rebuilds.rebuildOrder(orderId)).isEqualTo(1);

    verify(guardedAccess).assertWritable(orderId, actorId);
    verify(evidence).rebuild(orderId, caseId);
  }
}
