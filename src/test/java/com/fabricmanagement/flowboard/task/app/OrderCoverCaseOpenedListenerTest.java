package com.fabricmanagement.flowboard.task.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.flowboard.board.domain.Board;
import com.fabricmanagement.flowboard.board.domain.BoardType;
import com.fabricmanagement.flowboard.board.infra.repository.BoardRepository;
import com.fabricmanagement.flowboard.routing.domain.RoutingPoolKey;
import com.fabricmanagement.flowboard.routing.domain.port.in.GovernedTaskRoutingPort;
import com.fabricmanagement.flowboard.task.domain.Task;
import com.fabricmanagement.flowboard.task.domain.TaskCreation;
import com.fabricmanagement.flowboard.task.domain.TaskSubject;
import com.fabricmanagement.flowboard.task.domain.TaskType;
import com.fabricmanagement.sales.salesorder.domain.event.OrderCoverCaseOpenedEvent;
import com.fabricmanagement.sales.salesorder.domain.port.OrderCoverCommandPort;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OrderCoverCaseOpenedListenerTest {
  @AfterEach
  void clearContext() {
    TenantContext.clear();
  }

  @Test
  void ignoresDelayedOpeningAfterTheCurrentCaseHasClosed() {
    UUID tenantId = UUID.randomUUID(), caseId = UUID.randomUUID(), orderId = UUID.randomUUID();
    TaskProvisioningService provisioning = mock(TaskProvisioningService.class);
    GovernedTaskRoutingPort routing = mock(GovernedTaskRoutingPort.class);
    BoardRepository boards = mock(BoardRepository.class);
    OrderCoverCommandPort orderCover = mock(OrderCoverCommandPort.class);
    when(orderCover.currentProvisioning(tenantId, orderId, caseId))
        .thenReturn(
            new OrderCoverCommandPort.ProvisioningSnapshot(
                false, orderId, "SO-closed", null, Set.of()));
    var event =
        new OrderCoverCaseOpenedEvent(
            tenantId, caseId, orderId, "stale-number", null, Set.of(UUID.randomUUID()));

    new OrderCoverCaseOpenedListener(
            provisioning,
            routing,
            boards,
            orderCover,
            org.mockito.Mockito.mock(
                com.fabricmanagement.flowboard.task.infra.repository.TaskTransitionTransactionGuard
                    .class))
        .onOpened(event);

    verify(orderCover).currentProvisioning(tenantId, orderId, caseId);
    verifyNoInteractions(provisioning, routing, boards);
  }

  @Test
  void provisionsTheGovernedTaskThenAttachesAndRoutesIt() {
    UUID tenantId = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    UUID lineId = UUID.randomUUID();
    UUID staleLineId = UUID.randomUUID();
    UUID boardId = UUID.randomUUID();
    UUID taskId = UUID.randomUUID();
    TaskProvisioningService provisioning = mock(TaskProvisioningService.class);
    GovernedTaskRoutingPort routing = mock(GovernedTaskRoutingPort.class);
    BoardRepository boards = mock(BoardRepository.class);
    OrderCoverCommandPort orderCover = mock(OrderCoverCommandPort.class);
    Board board = mock(Board.class);
    Task task = mock(Task.class);
    when(board.getId()).thenReturn(boardId);
    when(task.getId()).thenReturn(taskId);
    when(boards.findByTenantIdAndBoardType(tenantId, BoardType.GLOBAL))
        .thenReturn(Optional.of(board));
    when(provisioning.createOrSynchronizeActive(any())).thenReturn(task);
    when(orderCover.currentProvisioning(tenantId, orderId, caseId))
        .thenReturn(
            new OrderCoverCommandPort.ProvisioningSnapshot(
                true, orderId, "SO-1", LocalDate.of(2026, 10, 1), Set.of(lineId)));
    OrderCoverCaseOpenedEvent event =
        new OrderCoverCaseOpenedEvent(
            tenantId, caseId, orderId, "SO-stale", LocalDate.of(2026, 9, 1), Set.of(staleLineId));

    new OrderCoverCaseOpenedListener(
            provisioning,
            routing,
            boards,
            orderCover,
            org.mockito.Mockito.mock(
                com.fabricmanagement.flowboard.task.infra.repository.TaskTransitionTransactionGuard
                    .class))
        .onOpened(event);

    ArgumentCaptor<TaskCreation> creation = ArgumentCaptor.forClass(TaskCreation.class);
    verify(provisioning).createOrSynchronizeActive(creation.capture());
    assertThat(creation.getValue().taskType()).isEqualTo(TaskType.ORDER_COVER);
    assertThat(creation.getValue().entityId()).isEqualTo(orderId);
    assertThat(creation.getValue().title()).isEqualTo("Cover sales order SO-1");
    assertThat(creation.getValue().deadline()).isEqualTo(LocalDate.of(2026, 10, 1));
    assertThat(creation.getValue().generationKey()).isEqualTo("ORDER_COVER:" + caseId);
    assertThat(creation.getValue().affectedSubjects())
        .containsExactly(new TaskSubject("SALES_ORDER_LINE", lineId));
    var ordered = inOrder(orderCover, routing);
    ordered.verify(orderCover).attachTask(tenantId, caseId, taskId);
    ordered.verify(routing).evaluate(tenantId, RoutingPoolKey.ORDER_COVER, taskId);
  }
}
