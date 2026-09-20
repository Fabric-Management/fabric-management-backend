package com.fabricmanagement.flowboard.task.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.flowboard.task.app.adapter.CancelOrderCoverPayload;
import com.fabricmanagement.flowboard.task.app.adapter.CancelOrderCoverTaskAction;
import com.fabricmanagement.flowboard.task.domain.DomainTaskActionResult;
import com.fabricmanagement.flowboard.task.domain.Task;
import com.fabricmanagement.flowboard.task.domain.TaskActionCommand;
import com.fabricmanagement.flowboard.task.infra.repository.TaskRepository;
import com.fabricmanagement.platform.user.domain.SystemUser;
import com.fabricmanagement.sales.salesorder.domain.event.SalesOrderCancelledEvent;
import com.fabricmanagement.sales.salesorder.domain.port.OrderCoverCommandPort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OrderCoverCancellationTest {
  @AfterEach
  void clearContext() {
    TenantContext.clear();
  }

  @Test
  void cancellationClosesTheCaseThroughTheSharedTaskActionResult() {
    UUID tenantId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();
    TenantContext.setCurrentTenantId(tenantId);
    OrderCoverCommandPort orderCover = mock(OrderCoverCommandPort.class);
    when(orderCover.cancel(tenantId, orderId)).thenReturn(caseId);
    Task task = mock(Task.class);
    when(task.getEntityId()).thenReturn(orderId);
    CancelOrderCoverPayload payload = new CancelOrderCoverPayload();
    TaskActionCommand command =
        new TaskActionCommand(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "cancel-key",
            CancelOrderCoverTaskAction.ACTION,
            "a".repeat(64),
            1,
            payload);

    var result = new CancelOrderCoverTaskAction(orderCover).execute(task, command);

    assertThat(result)
        .isEqualTo(
            new DomainTaskActionResult.Accepted(
                "ORDER_COVER_CANCELLATION", caseId, java.util.Set.of()));
    verify(orderCover).cancel(tenantId, orderId);
  }

  @Test
  void cancellationListenerUsesTheRetryingTransitionBoundaryForAnExistingTask() {
    UUID tenantId = UUID.randomUUID(), orderId = UUID.randomUUID(), taskId = UUID.randomUUID();
    TaskRepository tasks = mock(TaskRepository.class);
    TaskTransitionExecutor transitions = mock(TaskTransitionExecutor.class);
    OrderCoverCommandPort orderCover = mock(OrderCoverCommandPort.class);
    Task task = mock(Task.class);
    when(task.getId()).thenReturn(taskId);
    when(task.getVersion()).thenReturn(7L);
    when(tasks.findByTenantIdAndEntityTypeAndEntityIdAndTaskTypeAndIsActiveTrueAndClosedAtIsNull(
            tenantId,
            "SALES_ORDER",
            orderId,
            com.fabricmanagement.flowboard.task.domain.TaskType.ORDER_COVER))
        .thenReturn(Optional.of(task));

    new OrderCoverCancellationListener(
            tasks,
            transitions,
            orderCover,
            org.mockito.Mockito.mock(
                com.fabricmanagement.flowboard.task.infra.repository.TaskTransitionTransactionGuard
                    .class))
        .onCancelled(new SalesOrderCancelledEvent(tenantId, orderId, "SO-1", List.of()));

    var command = org.mockito.ArgumentCaptor.forClass(TaskActionCommand.class);
    verify(transitions).execute(command.capture());
    assertThat(command.getValue().taskId()).isEqualTo(taskId);
    assertThat(command.getValue().actorId()).isEqualTo(SystemUser.ID);
    assertThat(command.getValue().expectedVersion()).isEqualTo(7L);
    assertThat(command.getValue().actionKey()).isEqualTo(CancelOrderCoverTaskAction.ACTION);
    verify(orderCover, org.mockito.Mockito.never()).cancel(tenantId, orderId);
  }

  @Test
  void legacyCancellationWithoutAnOrderCoverTaskUsesTheOptionalCasePath() {
    UUID tenantId = UUID.randomUUID(), orderId = UUID.randomUUID();
    TaskRepository tasks = mock(TaskRepository.class);
    TaskTransitionExecutor transitions = mock(TaskTransitionExecutor.class);
    OrderCoverCommandPort orderCover = mock(OrderCoverCommandPort.class);
    when(tasks.findByTenantIdAndEntityTypeAndEntityIdAndTaskTypeAndIsActiveTrueAndClosedAtIsNull(
            tenantId,
            "SALES_ORDER",
            orderId,
            com.fabricmanagement.flowboard.task.domain.TaskType.ORDER_COVER))
        .thenReturn(Optional.empty());

    var guard =
        org.mockito.Mockito.mock(
            com.fabricmanagement.flowboard.task.infra.repository.TaskTransitionTransactionGuard
                .class);
    new OrderCoverCancellationListener(tasks, transitions, orderCover, guard)
        .onCancelled(new SalesOrderCancelledEvent(tenantId, orderId, "SO-legacy", List.of()));

    // The bounded lock timeout must be set before the path that takes order and case row locks.
    var ordered = org.mockito.Mockito.inOrder(guard, orderCover);
    ordered.verify(guard).setBoundedLockTimeout();
    ordered.verify(orderCover).cancelIfPresent(tenantId, orderId);
    verifyNoInteractions(transitions);
  }
}
