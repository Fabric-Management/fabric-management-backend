package com.fabricmanagement.finance.collection.app;

import com.fabricmanagement.finance.collection.domain.CollectionCheque;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.ChequeState;
import com.fabricmanagement.finance.collection.infra.repository.CollectionChequeRepository;
import com.fabricmanagement.finance.collection.infra.repository.CollectionPaymentMatchRepository;
import com.fabricmanagement.finance.payment.domain.Payment;
import com.fabricmanagement.finance.payment.domain.PaymentDirection;
import com.fabricmanagement.finance.payment.domain.PaymentMethod;
import com.fabricmanagement.finance.payment.domain.PaymentStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * PAYMENT-PLAN-9 R5: how much of a payment instalment matching may still use. The one calculation
 * behind both the customer view and the match guard, so the screen can never offer more than the
 * command accepts: payment amount minus active collection matches minus amounts held for refund,
 * never below zero. Invoice allocations are a separate view of the same money and are not
 * subtracted ({@code Payment.getUnallocatedAmount()} is deliberately not used).
 */
@Component
@RequiredArgsConstructor
public class CollectionPaymentAvailability {
  private final CollectionPaymentMatchRepository matches;
  private final CollectionRefundLedger refunds;
  private final CollectionChequeRepository cheques;

  /**
   * @param eligible whether the payment may be offered for instalment matching at all: active,
   *     inbound, neither voided, reversed nor a reversal, and, for a cheque payment, the verified
   *     clearing payment of a cleared cheque (R4)
   * @param clearedChequeId the cleared cheque this payment is the clearing of, or null
   */
  public record Availability(
      UUID paymentId,
      boolean eligible,
      BigDecimal matched,
      BigDecimal heldForRefund,
      BigDecimal available,
      UUID clearedChequeId) {}

  /** One payment, as the match guard reads it under the collection lock. */
  public Availability of(UUID tenant, Payment payment) {
    UUID cheque =
        cheques
            .findByTenantIdAndClearedPaymentIdInAndState(
                tenant, List.of(payment.getId()), ChequeState.CLEARED)
            .stream()
            .map(CollectionCheque::getId)
            .findFirst()
            .orElse(null);
    return assemble(
        payment,
        matches.matchedPayment(tenant, payment.getId()),
        refunds.held(tenant, payment.getId()),
        cheque);
  }

  public Map<UUID, Availability> of(UUID tenant, List<Payment> payments) {
    if (payments.isEmpty()) return Map.of();
    var ids = payments.stream().map(Payment::getId).toList();
    Map<UUID, BigDecimal> matched =
        matches.matchedPayments(tenant, ids).stream()
            .collect(
                Collectors.toMap(
                    CollectionPaymentMatchRepository.Balance::getId,
                    CollectionPaymentMatchRepository.Balance::getAmount));
    var held = refunds.heldByPayment(tenant, ids);
    Map<UUID, UUID> clearedBy =
        cheques
            .findByTenantIdAndClearedPaymentIdInAndState(tenant, ids, ChequeState.CLEARED)
            .stream()
            .collect(
                Collectors.toMap(CollectionCheque::getClearedPaymentId, CollectionCheque::getId));
    return payments.stream()
        .collect(
            Collectors.toMap(
                Payment::getId,
                payment ->
                    assemble(
                        payment,
                        matched.getOrDefault(payment.getId(), BigDecimal.ZERO),
                        held.getOrDefault(payment.getId(), BigDecimal.ZERO),
                        clearedBy.get(payment.getId())),
                (first, second) -> first,
                java.util.LinkedHashMap::new));
  }

  /** The formula, once: amount minus active matches minus refund holds, never below zero. */
  private static Availability assemble(
      Payment payment, BigDecimal matched, BigDecimal held, UUID clearedCheque) {
    BigDecimal used = matched == null ? BigDecimal.ZERO : matched;
    BigDecimal kept = held == null ? BigDecimal.ZERO : held;
    return new Availability(
        payment.getId(),
        eligible(payment, clearedCheque),
        used,
        kept,
        payment.getAmount().getAmount().subtract(used).subtract(kept).max(BigDecimal.ZERO),
        clearedCheque);
  }

  private static boolean eligible(Payment payment, UUID clearedCheque) {
    return Boolean.TRUE.equals(payment.getIsActive())
        && payment.getDirection() == PaymentDirection.INBOUND
        && payment.getStatus() != PaymentStatus.VOIDED
        && !payment.isReversed()
        && !payment.isReversal()
        && (payment.getMethod() != PaymentMethod.CHEQUE || clearedCheque != null);
  }
}
