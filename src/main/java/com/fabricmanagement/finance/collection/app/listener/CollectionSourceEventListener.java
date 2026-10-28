package com.fabricmanagement.finance.collection.app.listener;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.finance.collection.app.CollectionPaymentObservationService;
import com.fabricmanagement.finance.collection.app.CollectionProjectionService;
import com.fabricmanagement.finance.payment.domain.event.PaymentVoidedEvent;
import com.fabricmanagement.sales.salesorder.domain.event.SalesOrderPaymentPlanAgreedEvent;
import com.fabricmanagement.sales.salesorder.domain.event.SalesOrderPaymentPlanCancelledEvent;
import com.fabricmanagement.sales.salesorder.domain.event.SalesOrderPaymentPlanRevisedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CollectionSourceEventListener {
  private final CollectionProjectionService projections;
  private final CollectionPaymentObservationService payments;
  private final IdempotentEventHandler replay;

  @ApplicationModuleListener
  public void agreed(SalesOrderPaymentPlanAgreedEvent event) {
    run(event, "agreed", () -> projections.synchronize(event.getSalesOrderId()));
  }

  @ApplicationModuleListener
  public void revised(SalesOrderPaymentPlanRevisedEvent event) {
    run(event, "revised", () -> projections.synchronize(event.getSalesOrderId()));
  }

  @ApplicationModuleListener
  public void cancelled(SalesOrderPaymentPlanCancelledEvent event) {
    run(event, "cancelled", () -> projections.synchronize(event.getSalesOrderId()));
  }

  @ApplicationModuleListener
  public void voided(PaymentVoidedEvent event) {
    run(event, "voided", () -> payments.observe(event.getPaymentId()));
  }

  private void run(DomainEvent event, String method, Runnable action) {
    TenantContext.executeInTenantContext(
        event.getTenantId(),
        () -> {
          var previous = TenantContext.getCurrentUserId();
          TenantContext.setCurrentUserId(TenantContext.SYSTEM_ACTOR_ID);
          try {
            replay.executeOnce(event.getEventId(), getClass(), method, action);
          } finally {
            TenantContext.setCurrentUserId(previous);
          }
          return null;
        });
  }
}
