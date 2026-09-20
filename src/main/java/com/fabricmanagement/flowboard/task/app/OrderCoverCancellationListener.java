package com.fabricmanagement.flowboard.task.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.flowboard.task.app.adapter.*;
import com.fabricmanagement.flowboard.task.domain.*;
import com.fabricmanagement.flowboard.task.infra.repository.TaskRepository;
import com.fabricmanagement.platform.user.domain.SystemUser;
import com.fabricmanagement.sales.salesorder.domain.OrderCoverFingerprint;
import com.fabricmanagement.sales.salesorder.domain.event.SalesOrderCancelledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderCoverCancellationListener {
  private final TaskRepository tasks;
  private final TaskTransitionExecutor transitions;
  private final com.fabricmanagement.sales.salesorder.domain.port.OrderCoverCommandPort orderCover;
  private final com.fabricmanagement.flowboard.task.infra.repository.TaskTransitionTransactionGuard
      transactionGuard;

  @ApplicationModuleListener
  public void onCancelled(SalesOrderCancelledEvent event) {
    TenantContext.executeInTenantContext(
        event.getTenantId(),
        () -> {
          // The module listener already runs in its own transaction. Bound its lock waits before
          // any
          // persistence access: the no-task path below takes order and case row locks in THIS
          // transaction, outside the orchestrator that guards the task path (FE-ARCH-5b-3 §5.1).
          transactionGuard.setBoundedLockTimeout();
          TenantContext.setCurrentUserId(SystemUser.ID);
          var task =
              tasks
                  .findByTenantIdAndEntityTypeAndEntityIdAndTaskTypeAndIsActiveTrueAndClosedAtIsNull(
                      event.getTenantId(),
                      "SALES_ORDER",
                      event.getSalesOrderId(),
                      TaskType.ORDER_COVER);
          if (task.isPresent()) {
            var value = task.get();
            var payload = new CancelOrderCoverPayload();
            transitions.execute(
                new TaskActionCommand(
                    value.getId(),
                    SystemUser.ID,
                    event.getEventId().toString(),
                    CancelOrderCoverTaskAction.ACTION,
                    OrderCoverFingerprint.of(payload),
                    value.getVersion(),
                    payload));
          } else {
            orderCover.cancelIfPresent(event.getTenantId(), event.getSalesOrderId());
          }
        });
  }
}
