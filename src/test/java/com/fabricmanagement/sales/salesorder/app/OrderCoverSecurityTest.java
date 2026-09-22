package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.sales.salesorder.domain.*;
import com.fabricmanagement.sales.salesorder.domain.port.OrderCoverCapabilityPort;
import com.fabricmanagement.sales.salesorder.infra.repository.*;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.*;

class OrderCoverSecurityTest {
  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void unreadableOrderIsIndistinguishableFromMissingOrder() {
    UUID tenant = UUID.randomUUID(), orderId = UUID.randomUUID(), actor = UUID.randomUUID();
    TenantContext.setCurrentTenantId(tenant);
    SalesOrderRepository orders = mock(SalesOrderRepository.class);
    SalesOrderAccessPolicy access = mock(SalesOrderAccessPolicy.class);
    SalesOrder order = mock(SalesOrder.class);
    when(orders.findByTenantIdAndId(tenant, orderId)).thenReturn(Optional.of(order));
    when(access.canRead(tenant, actor, order)).thenReturn(false);
    var service =
        new OrderCoverQueryService(
            new OrderCoverObjectAccess(orders, access),
            orders,
            mock(OrderCoverCaseRepository.class),
            mock(OrderCoverCaseLineRepository.class),
            mock(OrderCoverEvidenceRepository.class),
            mock(OrderCoverResultRepository.class),
            mock(OrderCoverLineResultRepository.class),
            mock(com.fabricmanagement.sales.salesorder.domain.port.OrderCoverCapabilityPort.class),
            Clock.systemUTC());
    assertThatThrownBy(() -> service.assertReadable(orderId, actor))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("Sales order not found");
  }

  @Test
  void decisionIsNotOfferedUntilActionableEvidenceExists() {
    UUID tenant = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    UUID actor = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();
    UUID taskId = UUID.randomUUID();
    TenantContext.setCurrentTenantId(tenant);
    SalesOrder order = mock(SalesOrder.class);
    OrderCoverObjectAccess access = mock(OrderCoverObjectAccess.class);
    when(access.readable(orderId, actor)).thenReturn(order);
    when(order.getOrderNumber()).thenReturn("SO-1");
    OrderCoverCase coverCase = mock(OrderCoverCase.class);
    when(coverCase.getId()).thenReturn(caseId);
    when(coverCase.getTaskId()).thenReturn(taskId);
    when(coverCase.getState()).thenReturn(OrderCoverCaseState.OPEN);
    OrderCoverCaseRepository cases = mock(OrderCoverCaseRepository.class);
    when(cases.findByTenantIdAndSalesOrderId(tenant, orderId)).thenReturn(Optional.of(coverCase));
    OrderCoverCapabilityPort capabilities = mock(OrderCoverCapabilityPort.class);
    when(capabilities.evaluate(tenant, orderId, taskId, actor, true, false))
        .thenReturn(
            new OrderCoverCapabilityPort.Snapshot(
                2L, "BACKLOG", List.of(actor), false, "EVIDENCE_UNKNOWN"));
    OrderCoverQueryService service =
        new OrderCoverQueryService(
            access,
            mock(SalesOrderRepository.class),
            cases,
            mock(OrderCoverCaseLineRepository.class),
            mock(OrderCoverEvidenceRepository.class),
            mock(OrderCoverResultRepository.class),
            mock(OrderCoverLineResultRepository.class),
            capabilities,
            Clock.systemUTC());

    var action = service.detail(orderId, actor).actions().getFirst();

    assertThat(action.allowed()).isFalse();
    assertThat(action.reason().code())
        .isEqualTo(
            com.fabricmanagement.sales.salesorder.dto.OrderCoverDetail.DecisionBlockedReasonCode
                .EVIDENCE_UNKNOWN);
  }
}
