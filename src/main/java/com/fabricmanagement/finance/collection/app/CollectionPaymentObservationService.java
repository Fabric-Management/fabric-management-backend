package com.fabricmanagement.finance.collection.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.finance.collection.infra.repository.CollectionObligationRepository;
import com.fabricmanagement.finance.collection.infra.repository.CollectionPaymentMatchRepository;
import com.fabricmanagement.finance.payment.domain.PaymentStatus;
import com.fabricmanagement.finance.payment.infra.repository.PaymentRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Observes a real Payment void; this is not an invented financial reversal with a guessed effective
 * date.
 */
@Service
@RequiredArgsConstructor
public class CollectionPaymentObservationService {
  private final CollectionCommandStore commands;
  private final PaymentRepository payments;
  private final CollectionPaymentMatchRepository matches;
  private final CollectionObligationRepository obligations;
  private final CollectionEvents events;

  @Transactional
  public void observe(UUID paymentId) {
    commands.lock();
    UUID tenant = TenantContext.requireTenantId();
    var payment = payments.findByTenantIdAndId(tenant, paymentId).orElse(null);
    if (payment == null || payment.getStatus() != PaymentStatus.VOIDED) return;
    while (true) {
      var batch =
          matches.findByTenantIdAndPaymentIdAndPaymentInvalidatedAtIsNull(
              tenant, paymentId, PageRequest.of(0, 200));
      if (batch.isEmpty()) return;
      for (var match : batch) {
        match.observeVoidedPayment(payment.getUpdatedAt(), commands.now());
        matches.save(match);
        var row = obligations.findByTenantIdAndId(tenant, match.getObligationId()).orElseThrow();
        row.financialChange();
        obligations.save(row);
        events.changed(row.getId());
      }
      matches.flush();
      obligations.flush();
    }
  }
}
