package com.fabricmanagement.sales.ownership.app;

import com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.ownership.domain.event.CustomerOwnershipResolvedEvent;
import com.fabricmanagement.sales.ownership.infra.repository.OwnershipTriageCaseLogRepository;
import com.fabricmanagement.sales.quote.app.QuoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomerOwnershipResolvedListener {

  private final IdempotentEventHandler idempotentEventHandler;
  private final QuoteService quoteService;
  private final OwnershipTriageCaseLogRepository caseLogRepository;

  @ApplicationModuleListener
  public void onCustomerOwnershipResolved(CustomerOwnershipResolvedEvent event) {
    TenantContext.executeInTenantContext(
        event.getTenantId(),
        () ->
            idempotentEventHandler.executeOnce(
                event.getEventId(),
                this.getClass(),
                "onCustomerOwnershipResolved",
                () -> resolve(event)));
  }

  private void resolve(CustomerOwnershipResolvedEvent event) {
    int quotesBackfilled =
        quoteService.backfillUnassignedActionableQuotes(
            event.getTenantId(), event.getCustomerId(), event.getRepresentativeId());
    int casesResolved =
        caseLogRepository.resolveOpenCases(
            event.getTenantId(), event.getCustomerId(), event.getResolvedAt());
    log.info(
        "Customer ownership triage resolved: tenantId={}, customerId={}, quotesBackfilled={}, "
            + "casesResolved={}",
        event.getTenantId(),
        event.getCustomerId(),
        quotesBackfilled,
        casesResolved);
  }
}
