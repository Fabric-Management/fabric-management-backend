package com.fabricmanagement.procurement.purchaseorder.app.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.procurement.purchaseorder.app.PoReceiveStatusService;
import com.fabricmanagement.production.execution.goodsreceipt.domain.GoodsReceiptSourceType;
import com.fabricmanagement.production.execution.goodsreceipt.domain.event.GoodsReceiptConfirmedEvent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class GoodsReceiptConfirmedPoListenerTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID PO_ID = UUID.randomUUID();

  @Mock private PoReceiveStatusService receiveStatusService;
  @Mock private IdempotentEventHandler idempotentEventHandler;

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  @Test
  void ignoresNonPurchaseOrderReceipts() {
    var listener =
        new GoodsReceiptConfirmedPoListener(receiveStatusService, idempotentEventHandler);

    listener.onGoodsReceiptConfirmed(event(GoodsReceiptSourceType.BATCH));

    verify(idempotentEventHandler, never()).executeOnce(any(), any(), any(), any());
  }

  @Test
  void executesOnceUsingTenantFromEvent() {
    doAnswer(
            invocation -> {
              invocation.getArgument(3, Runnable.class).run();
              return null;
            })
        .when(idempotentEventHandler)
        .executeOnce(any(), any(), any(), any());
    doAnswer(
            invocation -> {
              assertThat(TenantContext.requireTenantId()).isEqualTo(TENANT_ID);
              return null;
            })
        .when(receiveStatusService)
        .recomputeReceiveStatus(TENANT_ID, PO_ID);
    var listener =
        new GoodsReceiptConfirmedPoListener(receiveStatusService, idempotentEventHandler);

    listener.onGoodsReceiptConfirmed(event(GoodsReceiptSourceType.PURCHASE_ORDER));

    verify(idempotentEventHandler)
        .executeOnce(
            any(), eq(GoodsReceiptConfirmedPoListener.class), eq("onGoodsReceiptConfirmed"), any());
    verify(receiveStatusService).recomputeReceiveStatus(TENANT_ID, PO_ID);
  }

  @Test
  void retriesOnceAfterOptimisticLockFailure() {
    doAnswer(
            invocation -> {
              invocation.getArgument(3, Runnable.class).run();
              return null;
            })
        .when(idempotentEventHandler)
        .executeOnce(any(), any(), any(), any());
    org.mockito.Mockito.doThrow(new OptimisticLockingFailureException("conflict") {})
        .doNothing()
        .when(receiveStatusService)
        .recomputeReceiveStatus(TENANT_ID, PO_ID);
    var listener =
        new GoodsReceiptConfirmedPoListener(receiveStatusService, idempotentEventHandler);

    listener.onGoodsReceiptConfirmed(event(GoodsReceiptSourceType.PURCHASE_ORDER));

    verify(idempotentEventHandler, times(2)).executeOnce(any(), any(), any(), any());
    verify(receiveStatusService, times(2)).recomputeReceiveStatus(TENANT_ID, PO_ID);
  }

  private GoodsReceiptConfirmedEvent event(GoodsReceiptSourceType sourceType) {
    return GoodsReceiptConfirmedEvent.builder()
        .tenantId(TENANT_ID)
        .receiptId(UUID.randomUUID())
        .receiptNumber("GR-2026-0001")
        .sourceType(sourceType)
        .sourceId(PO_ID)
        .sourceLineId(UUID.randomUUID())
        .items(List.of())
        .build();
  }
}
