package com.fabricmanagement.finance.collection.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.finance.collection.domain.CollectionPaymentMatch;
import com.fabricmanagement.finance.collection.infra.repository.CollectionObligationRepository;
import com.fabricmanagement.finance.collection.infra.repository.CollectionPaymentMatchRepository;
import com.fabricmanagement.finance.period.app.port.FinancialPeriodGuard;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The one place a collection match is reversed, so a collection command and a payment reversal post
 * it the same way. It deliberately depends on repositories only: the payment reversal reaches it
 * through a port, and a service that uses {@code PaymentService} here would close a bean cycle.
 */
@Component
@RequiredArgsConstructor
public class CollectionMatchReversal {
  private final CollectionCommandStore commands;
  private final CollectionObligationRepository obligations;
  private final CollectionPaymentMatchRepository matches;
  private final CollectionEvents events;
  private final FinancialPeriodGuard periods;

  public void reverse(
      CollectionPaymentMatch match,
      LocalDate effective,
      Instant occurred,
      UUID actor,
      String reason) {
    UUID tenant = TenantContext.requireTenantId();
    periods.assertPostingAllowed(tenant, effective);
    match.reverse(effective, occurred, commands.now(), actor, reason);
    matches.saveAndFlush(match);
    var row = obligations.findByTenantIdAndId(tenant, match.getObligationId()).orElseThrow();
    row.financialChange();
    obligations.saveAndFlush(row);
    events.changed(row.getId());
  }
}
