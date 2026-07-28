package com.fabricmanagement.sales.ownership.app;

import com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler;
import com.fabricmanagement.platform.user.domain.event.UserDeactivatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserDeactivatedAssignmentListener {

  private final IdempotentEventHandler idempotentEventHandler;
  private final CustomerCommercialAssignmentService assignmentService;

  @ApplicationModuleListener
  public void onUserDeactivated(UserDeactivatedEvent event) {
    idempotentEventHandler.executeOnce(
        event.getEventId(),
        this.getClass(),
        "onUserDeactivated",
        () -> {
          int closed =
              assignmentService.closeAllForDeactivatedRepresentative(
                  event.getTenantId(), event.getUserId(), event.getOccurredAt());
          log.info(
              "Closed commercial assignments for deactivated representative: "
                  + "tenantId={}, representativeId={}, closed={}",
              event.getTenantId(),
              event.getUserId(),
              closed);
        });
  }
}
