package com.fabricmanagement.flowboard.generator.app;

import com.fabricmanagement.common.domain.event.production.WorkOrderRecipeAssignmentNeededEvent;
import com.fabricmanagement.production.execution.goodsreceipt.domain.event.GoodsReceiptConfirmedEvent;
import com.fabricmanagement.production.execution.workorder.domain.event.WorkOrderApprovedEvent;
import com.fabricmanagement.sales.salesorder.domain.event.SalesOrderConfirmedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Domain event dinleyerek otomatik Task oluşturur.
 *
 * <p>Akış: EventRouterService üzerinden ilgili adapter'ı bulup context oluşturur.
 *
 * <p>[F1 FIX] {@code @TransactionalEventListener(AFTER_COMMIT)} ile source event commit edilmeden
 * task oluşturulması önlenir.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SmartTaskGeneratorListener {

  private final EventRouterService eventRouterService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
  public void onSalesOrderConfirmed(SalesOrderConfirmedEvent event) {
    eventRouterService.route(event);
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
  public void onWorkOrderApproved(WorkOrderApprovedEvent event) {
    eventRouterService.route(event);
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
  public void onGoodsReceiptConfirmed(GoodsReceiptConfirmedEvent event) {
    eventRouterService.route(event);
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
  public void onRecipeAssignmentNeeded(WorkOrderRecipeAssignmentNeededEvent event) {
    eventRouterService.route(event);
  }
}
