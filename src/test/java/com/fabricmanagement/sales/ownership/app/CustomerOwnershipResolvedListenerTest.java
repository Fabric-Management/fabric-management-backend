package com.fabricmanagement.sales.ownership.app;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler;
import com.fabricmanagement.sales.ownership.domain.event.CustomerOwnershipResolvedEvent;
import com.fabricmanagement.sales.ownership.infra.repository.OwnershipTriageCaseLogRepository;
import com.fabricmanagement.sales.quote.app.QuoteService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerOwnershipResolvedListenerTest {

  @Mock private IdempotentEventHandler idempotentEventHandler;
  @Mock private QuoteService quoteService;
  @Mock private OwnershipTriageCaseLogRepository caseLogRepository;

  @Test
  void backfillsOnlyThroughTheScopedQuoteOperationAndResolvesTheLog() {
    CustomerOwnershipResolvedEvent event =
        new CustomerOwnershipResolvedEvent(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            Instant.parse("2026-07-28T12:00:00Z"));
    CustomerOwnershipResolvedListener listener =
        new CustomerOwnershipResolvedListener(
            idempotentEventHandler, quoteService, caseLogRepository);
    org.mockito.Mockito.doAnswer(
            invocation -> {
              invocation.getArgument(3, Runnable.class).run();
              return null;
            })
        .when(idempotentEventHandler)
        .executeOnce(
            eq(event.getEventId()),
            eq(CustomerOwnershipResolvedListener.class),
            eq("onCustomerOwnershipResolved"),
            any(Runnable.class));
    when(quoteService.backfillUnassignedActionableQuotes(
            event.getTenantId(), event.getCustomerId(), event.getRepresentativeId()))
        .thenReturn(2);
    when(caseLogRepository.resolveOpenCases(
            event.getTenantId(), event.getCustomerId(), event.getResolvedAt()))
        .thenReturn(1);

    listener.onCustomerOwnershipResolved(event);

    verify(quoteService)
        .backfillUnassignedActionableQuotes(
            event.getTenantId(), event.getCustomerId(), event.getRepresentativeId());
    verify(caseLogRepository)
        .resolveOpenCases(event.getTenantId(), event.getCustomerId(), event.getResolvedAt());
  }
}
