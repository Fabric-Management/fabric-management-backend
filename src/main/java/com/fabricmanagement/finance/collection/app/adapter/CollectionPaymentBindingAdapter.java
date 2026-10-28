package com.fabricmanagement.finance.collection.app.adapter;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.finance.collection.app.CollectionCommandStore;
import com.fabricmanagement.finance.collection.app.CollectionEvents;
import com.fabricmanagement.finance.collection.app.CollectionMatchReversal;
import com.fabricmanagement.finance.collection.app.CollectionRefundLedger;
import com.fabricmanagement.finance.collection.domain.CollectionPaymentMatch;
import com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException;
import com.fabricmanagement.finance.collection.infra.repository.CollectionChequeRepository;
import com.fabricmanagement.finance.collection.infra.repository.CollectionObligationRepository;
import com.fabricmanagement.finance.collection.infra.repository.CollectionPaymentMatchRepository;
import com.fabricmanagement.finance.payment.app.port.PaymentBindingPort;
import com.fabricmanagement.finance.payment.domain.PaymentStatus;
import com.fabricmanagement.finance.payment.infra.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tells payment what collection relies on, and releases the plain matches on its behalf. Payment
 * calls this port, so it depends on repositories and {@link CollectionMatchReversal} only: reaching
 * a collection service that uses {@code PaymentService} would close a bean cycle.
 */
@Component
@RequiredArgsConstructor
public class CollectionPaymentBindingAdapter implements PaymentBindingPort {
  private final CollectionPaymentMatchRepository matches;
  private final CollectionChequeRepository cheques;
  private final CollectionMatchReversal matchReversal;
  private final CollectionCommandStore commands;
  private final CollectionRefundLedger refundLedger;
  private final CollectionObligationRepository obligations;
  private final CollectionEvents events;
  private final PaymentRepository payments;

  @Override
  @Transactional(readOnly = true)
  public boolean isBoundToInstrument(UUID tenantId, UUID paymentId) {
    requireBoundTenant(tenantId);
    return cheques.existsByTenantIdAndClearedPaymentId(tenantId, paymentId);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean hasActiveMatches(UUID tenantId, UUID paymentId) {
    requireBoundTenant(tenantId);
    return matches.existsByTenantIdAndPaymentIdAndReversedAtIsNull(tenantId, paymentId);
  }

  @Override
  @Transactional(readOnly = true)
  public void assertMatchesReleasableOn(UUID tenantId, UUID paymentId, LocalDate effectiveDate) {
    requireBoundTenant(tenantId);
    matches.findByTenantIdAndPaymentIdAndReversedAtIsNull(tenantId, paymentId).stream()
        .map(CollectionPaymentMatch::getEffectiveDate)
        .max(Comparator.naturalOrder())
        .filter(effectiveDate::isBefore)
        .ifPresent(
            latest -> {
              throw CollectionRuleException.dateBeforeMatch(latest);
            });
  }

  @Override
  @Transactional(readOnly = true)
  public void assertRefundsReleasableOn(UUID tenantId, UUID paymentId, LocalDate effectiveDate) {
    requireBoundTenant(tenantId);
    LocalDate floor = refundLedger.releaseFloor(tenantId, paymentId);
    if (floor != null && effectiveDate.isBefore(floor))
      throw CollectionRuleException.refundDateBeforeHistory(floor);
  }

  @Override
  @Transactional
  public int releaseMatches(
      UUID tenantId, UUID paymentId, LocalDate effectiveDate, UUID actor, String reason) {
    requireBoundTenant(tenantId);
    var active = matches.findByTenantIdAndPaymentIdAndReversedAtIsNull(tenantId, paymentId);
    Instant occurred = commands.now();
    for (var match : active) matchReversal.reverse(match, effectiveDate, occurred, actor, reason);
    return active.size();
  }

  @Override
  @Transactional(readOnly = true)
  public BigDecimal refundHeld(UUID tenantId, UUID paymentId) {
    requireBoundTenant(tenantId);
    return refundLedger.held(tenantId, paymentId);
  }

  @Override
  @Transactional(readOnly = true)
  public BigDecimal refundPaid(UUID tenantId, UUID paymentId) {
    requireBoundTenant(tenantId);
    return refundLedger.paid(tenantId, paymentId);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean settlesRefund(UUID tenantId, UUID paymentId) {
    requireBoundTenant(tenantId);
    return !refundLedger.settledBy(tenantId, paymentId).isEmpty();
  }

  @Override
  @Transactional
  public int cancelUnpaidRefunds(
      UUID tenantId, UUID paymentId, LocalDate effectiveDate, UUID actor, String reason) {
    requireBoundTenant(tenantId);
    var cancelled =
        refundLedger.cancelUnpaidOf(
            tenantId, paymentId, effectiveDate, commands.now(), actor, reason);
    changed(tenantId, cancelled);
    return cancelled.size();
  }

  @Override
  @Transactional
  public int reopenRefunds(
      UUID tenantId, UUID paymentId, LocalDate effectiveDate, UUID actor, String reason) {
    requireBoundTenant(tenantId);
    var reopened =
        refundLedger.reopenSettledBy(
            tenantId,
            paymentId,
            effectiveDate,
            commands.now(),
            actor,
            reason,
            source -> receiptTakenBack(tenantId, source));
    changed(tenantId, reopened);
    return reopened.size();
  }

  /** True once the inbound payment a refund was paid from has itself been reversed or voided. */
  private boolean receiptTakenBack(UUID tenantId, UUID paymentId) {
    return payments
        .findByTenantIdAndId(tenantId, paymentId)
        .map(source -> source.isReversed() || source.getStatus() == PaymentStatus.VOIDED)
        .orElse(false);
  }

  /** The obligations whose refunds moved have a new financial state; tell whoever watches them. */
  private void changed(UUID tenantId, java.util.List<UUID> obligationIds) {
    for (UUID id : obligationIds.stream().distinct().toList()) {
      var row = obligations.findByTenantIdAndId(tenantId, id).orElseThrow();
      row.financialChange();
      obligations.saveAndFlush(row);
      events.changed(id);
    }
  }

  // Asking about another tenant is a bug; "not bound" would turn a safety check into a pass.
  private static void requireBoundTenant(UUID tenantId) {
    if (!TenantContext.requireTenantId().equals(tenantId))
      throw new IllegalStateException("Payment bindings were requested outside the bound tenant");
  }
}
