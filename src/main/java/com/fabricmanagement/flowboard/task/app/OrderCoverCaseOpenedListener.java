package com.fabricmanagement.flowboard.task.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.flowboard.board.domain.BoardType;
import com.fabricmanagement.flowboard.board.infra.repository.BoardRepository;
import com.fabricmanagement.flowboard.routing.domain.RoutingPoolKey;
import com.fabricmanagement.flowboard.routing.domain.port.in.GovernedTaskRoutingPort;
import com.fabricmanagement.flowboard.task.domain.*;
import com.fabricmanagement.sales.salesorder.domain.event.OrderCoverCaseOpenedEvent;
import com.fabricmanagement.sales.salesorder.domain.port.OrderCoverCommandPort;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderCoverCaseOpenedListener {
  private final TaskProvisioningService provisioning;
  private final GovernedTaskRoutingPort routing;
  private final BoardRepository boards;
  private final OrderCoverCommandPort orderCover;
  private final com.fabricmanagement.flowboard.task.infra.repository.TaskTransitionTransactionGuard
      transactionGuard;

  @ApplicationModuleListener
  public void onOpened(OrderCoverCaseOpenedEvent event) {
    TenantContext.executeInTenantContext(
        event.getTenantId(),
        () -> {
          // currentProvisioning locks the order, the case and its lines; bound those waits so a
          // stuck
          // settlement fails this delivery for redelivery instead of parking the listener thread.
          transactionGuard.setBoundedLockTimeout();
          var current =
              orderCover.currentProvisioning(
                  event.getTenantId(), event.getSalesOrderId(), event.getCaseId());
          if (!current.active()) return;
          var board =
              boards
                  .findByTenantIdAndBoardType(event.getTenantId(), BoardType.GLOBAL)
                  .orElseThrow(
                      () ->
                          new IllegalStateException("A GLOBAL board is required for order cover"));
          var subjects =
              current.unresolvedLineIds().stream()
                  .map(id -> new TaskSubject("SALES_ORDER_LINE", id))
                  .collect(Collectors.toSet());
          Task task =
              provisioning.createOrSynchronizeActive(
                  new TaskCreation(
                      board.getId(),
                      "Cover sales order " + current.orderNumber(),
                      "Decide how each open sales-order line will be covered",
                      TaskType.ORDER_COVER,
                      ModuleType.GENERAL,
                      Priority.HIGH,
                      current.deadline(),
                      null,
                      "SALES_ORDER",
                      current.orderId(),
                      "ORDER_COVER_CASE",
                      event.getCaseId(),
                      "ORDER_COVER:" + event.getCaseId(),
                      subjects));
          orderCover.attachTask(event.getTenantId(), event.getCaseId(), task.getId());
          routing.evaluate(event.getTenantId(), RoutingPoolKey.ORDER_COVER, task.getId());
        });
  }
}
