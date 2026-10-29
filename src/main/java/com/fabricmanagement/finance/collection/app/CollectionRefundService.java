package com.fabricmanagement.finance.collection.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.finance.collection.domain.CollectionCommandReceipt;
import com.fabricmanagement.finance.collection.domain.CollectionObligation;
import com.fabricmanagement.finance.collection.domain.CollectionRefund;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.RefundAction;
import com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException;
import com.fabricmanagement.finance.collection.dto.CollectionRequests;
import com.fabricmanagement.finance.collection.infra.repository.CollectionObligationRepository;
import com.fabricmanagement.finance.payment.domain.Payment;
import com.fabricmanagement.finance.payment.domain.PaymentDirection;
import com.fabricmanagement.finance.payment.domain.PaymentStatus;
import com.fabricmanagement.finance.period.app.port.FinancialPeriodGuard;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pays back, or stops owing, money a refund holds. A refund is paid by exactly one outbound Payment
 * of its amount; if that Payment is later voided or reversed the refund is owed again (see {@link
 * CollectionRefundLedger#reopenSettledBy}). Cancelling is only possible while it is unpaid: a paid
 * refund is first undone by undoing its Payment.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CollectionRefundService {
  private final CollectionCommandStore commands;
  private final CollectionPostingService posting;
  private final CollectionObligationRepository obligations;
  private final CollectionRefundLedger refunds;
  private final CollectionEvents events;
  private final FinancialPeriodGuard periods;

  public CollectionCommandReceipt settle(
      UUID refundId, CollectionRequests.SettleRefund request, UUID actor) {
    commands.validate(request);
    commands.lock();
    var tenant = TenantContext.requireTenantId();
    var view = view(tenant, refundId);
    var row = posting.writable(view.refund().getObligationId(), actor);
    String hash = commands.hash("SETTLE_REFUND", refundId, request);
    var replay = commands.replay(request.operationId(), actor, hash);
    if (replay.isPresent()) return replay.get();
    commands.version(request.expectedStep(), (long) view.step());
    var payment = posting.payment(request.paymentId());
    requireSettling(payment, view.refund());
    // An allocated payment's money is already an invoice's: it cannot also be the money paid out.
    if (payment.getAllocatedAmount().getAmount().signum() > 0)
      throw CollectionRuleException.conflict(
          "REFUND_PAYMENT_ALLOCATED",
          "Release this payment's invoice allocations before it pays a refund");
    if (!refunds.settledBy(tenant, payment.getId()).isEmpty())
      throw CollectionRuleException.conflict(
          "REFUND_PAYMENT_ALREADY_USED", "This payment already pays another refund");
    // The refund is paid on the day the money leaves, which is the payment's own date. A payment
    // dated before the refund was owed cannot pay it: say which day works, never move the event
    // away from the payment's date, or voiding that payment later could not reopen it on a day
    // inside the refund's history.
    LocalDate effective = payment.getPaymentDate();
    if (effective.isBefore(view.lastEffective()))
      throw CollectionRuleException.refundDateBeforeHistory(view.lastEffective());
    commands.time(effective, request.occurredAt());
    periods.assertPostingAllowed(tenant, effective);
    refunds.append(
        view,
        RefundAction.SETTLE,
        effective,
        request.occurredAt(),
        actor,
        request.reason(),
        payment.getId());
    changed(row);
    return posting.receipt(request.operationId(), actor, hash, row, payment.getId(), List.of());
  }

  public CollectionCommandReceipt cancel(
      UUID refundId, CollectionRequests.CancelRefund request, UUID actor) {
    commands.validate(request);
    commands.lock();
    var tenant = TenantContext.requireTenantId();
    var view = view(tenant, refundId);
    var row = posting.reversible(view.refund().getObligationId(), actor);
    String hash = commands.hash("CANCEL_REFUND", refundId, request);
    var replay = commands.replay(request.operationId(), actor, hash);
    if (replay.isPresent()) return replay.get();
    commands.version(request.expectedStep(), (long) view.step());
    commands.time(request.effectiveDate(), request.occurredAt());
    periods.assertPostingAllowed(tenant, request.effectiveDate());
    refunds.append(
        view,
        RefundAction.CANCEL,
        request.effectiveDate(),
        request.occurredAt(),
        actor,
        request.reason(),
        null);
    changed(row);
    return posting.receipt(request.operationId(), actor, hash, row, null, List.of());
  }

  private CollectionRefundLedger.View view(UUID tenant, UUID refundId) {
    var view = refunds.view(tenant, refundId);
    if (view == null) throw new NotFoundException("Collection refund not found");
    return view;
  }

  private static void requireSettling(Payment payment, CollectionRefund refund) {
    if (payment.getDirection() != PaymentDirection.OUTBOUND
        || payment.getStatus() == PaymentStatus.VOIDED
        || payment.isReversed()
        || payment.isReversal()
        || !payment.getTradingPartnerId().equals(refund.getPartnerId())
        || !payment.getCurrency().equals(refund.getCurrency())
        || payment.getAmount().getAmount().compareTo(refund.getAmount()) != 0)
      throw CollectionRuleException.conflict(
          "REFUND_PAYMENT_INCOMPATIBLE",
          "Pay a refund with one active outbound payment of exactly its amount, to the same"
              + " partner and currency");
  }

  private void changed(CollectionObligation row) {
    row.financialChange();
    obligations.saveAndFlush(row);
    events.changed(row.getId());
  }
}
