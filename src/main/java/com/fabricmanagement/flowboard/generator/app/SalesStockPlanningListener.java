package com.fabricmanagement.flowboard.generator.app;

import com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.orderintake.domain.event.SalesStockPlanningChangedEvent;
import com.fabricmanagement.sales.salesorder.domain.event.SalesOrderCancelledEvent;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SalesStockPlanningListener {
  private final SalesStockPlanningTaskService planning;
  private final IdempotentEventHandler replay;

  @ApplicationModuleListener
  public void changed(SalesStockPlanningChangedEvent event) {
    TenantContext.executeInTenantContext(
        event.getTenantId(),
        () -> {
          UUID previousActor = TenantContext.getCurrentUserId();
          TenantContext.setCurrentUserId(com.fabricmanagement.platform.user.domain.SystemUser.ID);
          try {
            replay.executeOnce(
                event.getEventId(),
                getClass(),
                "changed",
                () -> planning.synchronize(event.getDocumentId(), event.isQuote()));
          } finally {
            TenantContext.setCurrentUserId(previousActor);
          }
          return null;
        });
  }

  @ApplicationModuleListener
  public void cancelled(SalesOrderCancelledEvent event) {
    TenantContext.executeInTenantContext(
        event.getTenantId(),
        () -> {
          UUID previousActor = TenantContext.getCurrentUserId();
          TenantContext.setCurrentUserId(com.fabricmanagement.platform.user.domain.SystemUser.ID);
          try {
            replay.executeOnce(
                event.getEventId(),
                getClass(),
                "cancelled",
                () -> planning.synchronize(event.getSalesOrderId(), false));
          } finally {
            TenantContext.setCurrentUserId(previousActor);
          }
          return null;
        });
  }
}
