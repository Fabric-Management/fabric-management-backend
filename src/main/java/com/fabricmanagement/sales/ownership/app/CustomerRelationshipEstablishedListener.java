package com.fabricmanagement.sales.ownership.app;

import com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler;
import com.fabricmanagement.platform.tradingpartner.domain.event.CustomerRelationshipEstablishedEvent;
import com.fabricmanagement.sales.ownership.domain.OwnershipMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomerRelationshipEstablishedListener {

  private final IdempotentEventHandler idempotentEventHandler;
  private final OwnershipPolicyService ownershipPolicyService;
  private final CustomerCommercialAssignmentService assignmentService;

  @ApplicationModuleListener
  public void onCustomerRelationshipEstablished(CustomerRelationshipEstablishedEvent event) {
    idempotentEventHandler.executeOnce(
        event.getEventId(),
        this.getClass(),
        "onCustomerRelationshipEstablished",
        () -> {
          OwnershipMode mode =
              ownershipPolicyService.requirePolicy(event.getTenantId()).getDefaultMode();
          if (mode == OwnershipMode.EXEMPT || event.getAcquiredById() == null) {
            return;
          }
          assignmentService.createAcquisitionIfAbsent(
              event.getTenantId(),
              event.getCustomerId(),
              event.getAcquiredById(),
              event.getEstablishedAt());
          log.debug(
              "Handled customer relationship establishment: tenantId={}, customerId={}, gate={}",
              event.getTenantId(),
              event.getCustomerId(),
              event.getSourceGate());
        });
  }
}
