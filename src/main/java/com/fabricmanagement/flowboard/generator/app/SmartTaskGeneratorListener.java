package com.fabricmanagement.flowboard.generator.app;

import com.fabricmanagement.common.domain.event.production.WorkOrderRecipeAssignmentNeededEvent;
import com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler;
import com.fabricmanagement.production.execution.goodsreceipt.domain.event.GoodsReceiptConfirmedEvent;
import com.fabricmanagement.production.execution.workorder.domain.event.WorkOrderApprovedEvent;
import com.fabricmanagement.sales.salesorder.domain.event.SalesOrderConfirmedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Domain event dinleyerek otomatik Task oluşturur.
 *
 * <p>Akış: EventRouterService üzerinden ilgili adapter'ı bulup context oluşturur.
 *
 * <p>[F1 FIX] {@code @ApplicationModuleListener} ile source event commit edilmeden task
 * oluşturulması önlenir.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SmartTaskGeneratorListener {

  private final EventRouterService eventRouterService;
  private final IdempotentEventHandler idempotentHandler;

  @ApplicationModuleListener
  public void onSalesOrderConfirmed(SalesOrderConfirmedEvent event) {
    idempotentHandler.executeOnce(
        event.getEventId(),
        this.getClass(),
        "onSalesOrderConfirmed",
        () -> {
          eventRouterService.route(event);
        });
  }

  @ApplicationModuleListener
  public void onWorkOrderApproved(WorkOrderApprovedEvent event) {
    idempotentHandler.executeOnce(
        event.getEventId(),
        this.getClass(),
        "onWorkOrderApproved",
        () -> {
          eventRouterService.route(event);
        });
  }

  @ApplicationModuleListener
  public void onGoodsReceiptConfirmed(GoodsReceiptConfirmedEvent event) {
    idempotentHandler.executeOnce(
        event.getEventId(),
        this.getClass(),
        "onGoodsReceiptConfirmed",
        () -> {
          eventRouterService.route(event);
        });
  }

  @ApplicationModuleListener
  public void onRecipeAssignmentNeeded(WorkOrderRecipeAssignmentNeededEvent event) {
    idempotentHandler.executeOnce(
        event.getEventId(),
        this.getClass(),
        "onRecipeAssignmentNeeded",
        () -> {
          eventRouterService.route(event);
        });
  }
}
