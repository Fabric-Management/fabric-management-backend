package com.fabricmanagement.flowboard.routing.app.listener;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.flowboard.routing.app.*;
import com.fabricmanagement.flowboard.routing.domain.event.*;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoutingEventListener {
  private final RoutingRepairService repair;
  private final RoutingFailureAlertService alerts;

  @ApplicationModuleListener
  public void onRoutingPoolChanged(RoutingPoolChangedEvent event) {
    TenantContext.executeInTenantContext(
        event.getTenantId(),
        () -> {
          if (!repair.repair(event.getTenantId(), event.getPoolKey(), true).deliveryComplete())
            throw new IllegalStateException("Routing repair has incomplete alert delivery");
        });
  }

  @ApplicationModuleListener
  public void onRoutingFailureOpened(RoutingFailureOpenedEvent event) {
    TenantContext.executeInTenantContext(
        event.getTenantId(),
        () -> {
          if (!alerts.deliver(event.getTenantId(), event.getFailureId()))
            throw new IllegalStateException("Routing failure alert delivery remains owed");
        });
  }
}
