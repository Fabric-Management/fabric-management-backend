package com.fabricmanagement.production.execution.workorder.app.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.production.execution.workorder.app.WorkOrderService;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrder;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrderStatus;
import com.fabricmanagement.production.execution.workorder.infra.repository.WorkOrderRepository;
import com.fabricmanagement.sales.salesorder.domain.event.SalesOrderCancelledEvent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class WorkOrderSalesEventListenerTest {

  @Mock private WorkOrderService workOrderService;
  @Mock private WorkOrderRepository workOrderRepository;

  @InjectMocks private WorkOrderSalesEventListener listener;

  @Mock
  private com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler
      idempotentHandler;

  private UUID tenantId;
  private UUID orderId;
  private UUID lineId;

  @BeforeEach
  void setUp() {
    if (idempotentHandler != null) {
      org.mockito.Mockito.lenient()
          .doAnswer(
              invocation -> {
                ((Runnable) invocation.getArgument(3)).run();
                return null;
              })
          .when(idempotentHandler)
          .executeOnce(any(), any(), any(), any());
    }

    tenantId = UUID.randomUUID();
    orderId = UUID.randomUUID();
    lineId = UUID.randomUUID();
  }

  @Test
  void onSalesOrderCancelled_cancelsNonCompletedWorkOrders() {
    SalesOrderCancelledEvent event =
        new SalesOrderCancelledEvent(tenantId, orderId, "SO-123", List.of(lineId));

    WorkOrder wo1 = createWorkOrder(WorkOrderStatus.DRAFT);
    WorkOrder wo2 = createWorkOrder(WorkOrderStatus.IN_PROGRESS);

    when(workOrderRepository.findByTenantIdAndSalesOrderLineIdAndIsActiveTrueOrderByCreatedAtAsc(
            tenantId, lineId))
        .thenReturn(List.of(wo1, wo2));

    listener.onSalesOrderCancelled(event);

    verify(workOrderService).changeStatus(wo1.getId(), WorkOrderStatus.CANCELLED);
    verify(workOrderService).changeStatus(wo2.getId(), WorkOrderStatus.CANCELLED);
  }

  @Test
  void onSalesOrderCancelled_skipsCompletedWorkOrders() {
    SalesOrderCancelledEvent event =
        new SalesOrderCancelledEvent(tenantId, orderId, "SO-123", List.of(lineId));

    WorkOrder wo = createWorkOrder(WorkOrderStatus.COMPLETED);

    when(workOrderRepository.findByTenantIdAndSalesOrderLineIdAndIsActiveTrueOrderByCreatedAtAsc(
            tenantId, lineId))
        .thenReturn(List.of(wo));

    listener.onSalesOrderCancelled(event);

    verify(workOrderService, never()).changeStatus(any(), any());
  }

  @Test
  void onSalesOrderCancelled_idempotent_skipsAlreadyCancelled() {
    SalesOrderCancelledEvent event =
        new SalesOrderCancelledEvent(tenantId, orderId, "SO-123", List.of(lineId));

    WorkOrder wo = createWorkOrder(WorkOrderStatus.CANCELLED);

    when(workOrderRepository.findByTenantIdAndSalesOrderLineIdAndIsActiveTrueOrderByCreatedAtAsc(
            tenantId, lineId))
        .thenReturn(List.of(wo));

    listener.onSalesOrderCancelled(event);

    verify(workOrderService, never()).changeStatus(any(), any());
  }

  @Test
  void onSalesOrderCancelled_optimisticLockOnWO_swallowedPerWO() {
    SalesOrderCancelledEvent event =
        new SalesOrderCancelledEvent(tenantId, orderId, "SO-123", List.of(lineId));

    WorkOrder wo1 = createWorkOrder(WorkOrderStatus.IN_PROGRESS);
    WorkOrder wo2 = createWorkOrder(WorkOrderStatus.IN_PROGRESS);

    when(workOrderRepository.findByTenantIdAndSalesOrderLineIdAndIsActiveTrueOrderByCreatedAtAsc(
            tenantId, lineId))
        .thenReturn(List.of(wo1, wo2));

    // wo1 throws optimistic lock exception
    doThrow(new ObjectOptimisticLockingFailureException(WorkOrder.class, wo1.getId()))
        .when(workOrderService)
        .changeStatus(wo1.getId(), WorkOrderStatus.CANCELLED);

    listener.onSalesOrderCancelled(event);

    // Both should be attempted, wo1 failed and swallowed, wo2 succeeded
    verify(workOrderService).changeStatus(wo1.getId(), WorkOrderStatus.CANCELLED);
    verify(workOrderService).changeStatus(wo2.getId(), WorkOrderStatus.CANCELLED);
  }

  @Test
  void onSalesOrderCancelled_emptyLineIds_noop() {
    SalesOrderCancelledEvent event =
        new SalesOrderCancelledEvent(tenantId, orderId, "SO-123", List.of());

    listener.onSalesOrderCancelled(event);

    verify(workOrderRepository, never())
        .findByTenantIdAndSalesOrderLineIdAndIsActiveTrueOrderByCreatedAtAsc(any(), any());
    verify(workOrderService, never()).changeStatus(any(), any());
  }

  private WorkOrder createWorkOrder(WorkOrderStatus status) {
    WorkOrder wo =
        WorkOrder.builder()
            .workOrderNumber("WO-" + UUID.randomUUID().toString().substring(0, 4))
            .status(status)
            .build();
    ReflectionTestUtils.setField(wo, "id", UUID.randomUUID());
    return wo;
  }
}
