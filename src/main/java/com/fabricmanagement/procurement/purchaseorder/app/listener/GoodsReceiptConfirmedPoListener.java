package com.fabricmanagement.procurement.purchaseorder.app.listener;

import com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.procurement.purchaseorder.app.PoReceiveStatusService;
import com.fabricmanagement.production.execution.goodsreceipt.domain.GoodsReceiptSourceType;
import com.fabricmanagement.production.execution.goodsreceipt.domain.event.GoodsReceiptConfirmedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Re-derives PO receipt status after a confirmed goods receipt.
 *
 * <p>The processed-event guard follows the repository-wide listener convention. The derivation is
 * independently idempotent, so duplicate delivery also converges by construction.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GoodsReceiptConfirmedPoListener {

  private final PoReceiveStatusService receiveStatusService;
  private final IdempotentEventHandler idempotentEventHandler;

  @ApplicationModuleListener
  public void onGoodsReceiptConfirmed(GoodsReceiptConfirmedEvent event) {
    if (event.getSourceType() != GoodsReceiptSourceType.PURCHASE_ORDER) {
      return;
    }

    try {
      executeOnce(event);
    } catch (OptimisticLockingFailureException exception) {
      log.warn(
          "Retrying PO receive-status derivation after optimistic-lock conflict: eventId={}, po={}",
          event.getEventId(),
          event.getSourceId());
      executeOnce(event);
    }
  }

  private void executeOnce(GoodsReceiptConfirmedEvent event) {
    idempotentEventHandler.executeOnce(
        event.getEventId(),
        this.getClass(),
        "onGoodsReceiptConfirmed",
        () ->
            TenantContext.executeInTenantContext(
                event.getTenantId(),
                () -> {
                  receiveStatusService.recomputeReceiveStatus(
                      event.getTenantId(), event.getSourceId());
                  return null;
                }));
  }
}
