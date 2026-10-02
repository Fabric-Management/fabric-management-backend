package com.fabricmanagement.production.core.workorder.app;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.SalesOrderLineFulfilmentLock;
import com.fabricmanagement.production.core.workorder.api.WorkOrderSalesProductPort;
import com.fabricmanagement.production.core.workorder.domain.WorkOrder;
import com.fabricmanagement.production.core.workorder.domain.WorkOrderHold;
import com.fabricmanagement.production.core.workorder.domain.exception.WorkOrderHoldException;
import com.fabricmanagement.production.core.workorder.infra.repository.WorkOrderHoldRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkOrderExecutionGuardTest {
  @Mock SalesOrderLineFulfilmentLock locks;
  @Mock WorkOrderHoldRepository holds;
  @Mock WorkOrderSalesProductPort sales;
  WorkOrderExecutionGuard guard;
  WorkOrder order;

  @BeforeEach
  void setup() {
    guard = new WorkOrderExecutionGuard(locks, holds, sales);
    order =
        WorkOrder.builder()
            .salesOrderLineId(UUID.randomUUID())
            .outputProductId(UUID.randomUUID())
            .build();
    order.setId(UUID.randomUUID());
    order.setTenantId(UUID.randomUUID());
  }

  @Test
  void pendingOrConfirmedHoldBlocksNewWork() {
    when(holds.existsByTenantIdAndWorkOrderIdAndStatusIn(
            eq(order.getTenantId()), eq(order.getId()), any()))
        .thenReturn(true);
    assertThatThrownBy(() -> guard.requireExecutable(order))
        .isInstanceOf(WorkOrderHoldException.class);
    verify(locks).lock(order.getTenantId(), order.getSalesOrderLineId());
    verify(holds)
        .existsByTenantIdAndWorkOrderIdAndStatusIn(
            order.getTenantId(),
            order.getId(),
            java.util.EnumSet.of(
                WorkOrderHold.Status.HOLD_REQUESTED, WorkOrderHold.Status.HOLD_CONFIRMED));
  }

  @Test
  void aNewWorkOrderCannotBypassTheLinesHold() {
    when(holds.existsByTenantIdAndSalesOrderLineIdAndStatusIn(
            eq(order.getTenantId()), eq(order.getSalesOrderLineId()), any()))
        .thenReturn(true);
    assertThatThrownBy(() -> guard.requireExecutable(order))
        .isInstanceOf(WorkOrderHoldException.class);
  }

  @Test
  void customerCheckboxesCannotResumeAnOldProduct() {
    when(sales.matchesCurrentProduct(
            order.getTenantId(), order.getSalesOrderLineId(), order.getOutputProductId()))
        .thenReturn(false);
    assertThatThrownBy(() -> guard.requireCurrentProduct(order, order.getOutputProductId()))
        .isInstanceOf(WorkOrderHoldException.class)
        .hasMessageContaining("replan");
  }

  @Test
  void unchangedProductWithoutHoldCanProceed() {
    when(sales.matchesCurrentProduct(
            order.getTenantId(), order.getSalesOrderLineId(), order.getOutputProductId()))
        .thenReturn(true);
    guard.requireExecutable(order);
    verify(sales)
        .matchesCurrentProduct(
            order.getTenantId(), order.getSalesOrderLineId(), order.getOutputProductId());
  }
}
