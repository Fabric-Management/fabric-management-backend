package com.fabricmanagement.flowboard.collection.app.listener;

import com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.finance.collection.domain.event.CollectionObligationChangedEvent;
import com.fabricmanagement.flowboard.collection.app.CollectionFollowUpService;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CollectionFollowUpListener {
  private final CollectionFollowUpService service;
  private final IdempotentEventHandler replay;

  @ApplicationModuleListener
  public void changed(CollectionObligationChangedEvent event) {
    TenantContext.executeInTenantContext(
        event.getTenantId(),
        () -> {
          var actor = TenantContext.getCurrentUserId();
          TenantContext.setCurrentUserId(TenantContext.SYSTEM_ACTOR_ID);
          try {
            replay.executeOnce(
                event.getEventId(),
                getClass(),
                "changed",
                () -> service.synchronize(event.getObligationId()));
          } finally {
            TenantContext.setCurrentUserId(actor);
          }
          return null;
        });
  }
}
