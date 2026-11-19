package com.fabricmanagement.finance.collection.infra.repository;

import com.fabricmanagement.finance.collection.domain.CollectionPaymentMatch;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CollectionPaymentMatchRepository
    extends JpaRepository<CollectionPaymentMatch, UUID> {
  Optional<CollectionPaymentMatch> findByTenantIdAndId(UUID tenant, UUID id);

  Page<CollectionPaymentMatch> findByTenantIdAndObligationId(
      UUID tenant, UUID obligation, Pageable page);

  List<CollectionPaymentMatch> findByTenantIdAndChequeIdAndReversedAtIsNull(
      UUID tenant, UUID cheque);

  List<CollectionPaymentMatch> findByTenantIdAndPaymentIdAndReversedAtIsNull(
      UUID tenant, UUID payment);

  boolean existsByTenantIdAndPaymentIdAndReversedAtIsNull(UUID tenant, UUID payment);

  @Query(
      value =
          """
          SELECT COALESCE(sum(m.amount),0) FROM finance.collection_payment_match m
          JOIN finance.finance_payment p ON p.tenant_id=m.tenant_id AND p.id=m.payment_id
          WHERE m.tenant_id=:tenant AND m.payment_id=:payment AND m.reversed_at IS NULL
            AND p.status<>'VOIDED' AND p.is_active AND m.is_active
          """,
      nativeQuery = true)
  BigDecimal matchedPayment(UUID tenant, UUID payment);

  /** {@link #matchedPayment} for several payments at once, keyed by payment. */
  @Query(
      value =
          """
          SELECT m.payment_id AS id, sum(m.amount) AS amount FROM finance.collection_payment_match m
          JOIN finance.finance_payment p ON p.tenant_id=m.tenant_id AND p.id=m.payment_id
          WHERE m.tenant_id=:tenant AND m.payment_id IN (:payments) AND m.reversed_at IS NULL
            AND p.status<>'VOIDED' AND p.is_active AND m.is_active GROUP BY m.payment_id
          """,
      nativeQuery = true)
  List<Balance> matchedPayments(UUID tenant, List<UUID> payments);

  /**
   * PAYMENT-PLAN-9 R5: the customer's inbound payments that collection matches have not used up,
   * within the viewer's reach: active, not voided, neither reversed nor a reversal, a cheque
   * payment only as a cleared cheque's clearing payment, created by one of {@code principals}
   * unless {@code allScope}. Refund holds are applied afterwards by the shared availability
   * calculation. Newest first, at most {@code limit}, so a recent receipt is never pushed out by
   * old ones.
   */
  @Query(
      value =
          """
          SELECT p.id FROM finance.finance_payment p
          WHERE p.tenant_id=:tenant AND p.trading_partner_id=:partner AND p.direction='INBOUND'
            AND p.status<>'VOIDED' AND p.is_active
            AND p.reverses_payment_id IS NULL AND p.reversed_by_payment_id IS NULL
            AND (:allScope OR p.created_by IN (:principals))
            AND (p.method<>'CHEQUE' OR EXISTS (SELECT 1 FROM finance.collection_cheque c
              WHERE c.tenant_id=p.tenant_id AND c.cleared_payment_id=p.id AND c.state='CLEARED'))
            AND p.amount > COALESCE((SELECT sum(m.amount) FROM finance.collection_payment_match m
              WHERE m.tenant_id=p.tenant_id AND m.payment_id=p.id AND m.reversed_at IS NULL
                AND m.is_active),0)
          ORDER BY p.payment_date DESC, p.created_at DESC, p.id DESC LIMIT :limit
          """,
      nativeQuery = true)
  List<UUID> unmatchedPaymentIds(
      UUID tenant,
      UUID partner,
      boolean allScope,
      java.util.Collection<UUID> principals,
      int limit);

  /**
   * Active invoice allocations per payment: shown beside collection availability, never subtracted.
   */
  @Query(
      value =
          """
          SELECT a.payment_id AS id, sum(a.amount) AS amount FROM finance.finance_payment_allocation a
          WHERE a.tenant_id=:tenant AND a.payment_id IN (:payments) AND a.is_active
          GROUP BY a.payment_id
          """,
      nativeQuery = true)
  List<Balance> invoiceAllocated(UUID tenant, List<UUID> payments);

  @Query(
      value =
          """
          SELECT m.obligation_id AS id, sum(m.amount) AS amount FROM finance.collection_payment_match m
          JOIN finance.finance_payment p ON p.tenant_id=m.tenant_id AND p.id=m.payment_id
          WHERE m.tenant_id=:tenant AND m.obligation_id IN (:ids) AND m.reversed_at IS NULL
            AND p.status<>'VOIDED' AND p.is_active AND m.is_active
          GROUP BY m.obligation_id
          """,
      nativeQuery = true)
  List<Balance> settled(UUID tenant, List<UUID> ids);

  @Query(
      value =
          """
          SELECT COALESCE(sum(m.amount),0) FROM finance.collection_payment_match m
          JOIN finance.finance_payment p ON p.tenant_id=m.tenant_id AND p.id=m.payment_id
          WHERE m.tenant_id=:tenant AND m.obligation_id=:obligation AND m.cheque_id IS NULL
            AND m.reversed_at IS NULL AND p.status<>'VOIDED' AND p.is_active AND m.is_active
          """,
      nativeQuery = true)
  BigDecimal nonChequeSettled(UUID tenant, UUID obligation);

  @Query(
      value =
          """
          SELECT m.obligation_id AS id, sum(m.amount) AS amount FROM finance.collection_payment_match m
          JOIN finance.finance_payment p ON p.tenant_id=m.tenant_id AND p.id=m.payment_id
          WHERE m.tenant_id=:tenant AND m.obligation_id IN (:ids) AND m.cheque_id IS NULL
            AND m.reversed_at IS NULL AND p.status<>'VOIDED' AND p.is_active AND m.is_active
          GROUP BY m.obligation_id
          """,
      nativeQuery = true)
  List<Balance> nonChequeSettledFor(UUID tenant, List<UUID> ids);

  /**
   * The matches that currently hold an expectation's money, latest recorded first, so a surplus is
   * taken from the receipts that caused it. Together they hold exactly what {@link #settled} sums.
   */
  @Query(
      value =
          """
          SELECT m.* FROM finance.collection_payment_match m
          JOIN finance.finance_payment p ON p.tenant_id=m.tenant_id AND p.id=m.payment_id
          WHERE m.tenant_id=:tenant AND m.obligation_id=:obligation AND m.reversed_at IS NULL
            AND p.status<>'VOIDED' AND p.is_active AND m.is_active
          ORDER BY m.created_at DESC, m.id DESC
          """,
      nativeQuery = true)
  List<CollectionPaymentMatch> holdingNewestFirst(UUID tenant, UUID obligation);

  Page<CollectionPaymentMatch> findByTenantIdAndPaymentIdAndPaymentInvalidatedAtIsNull(
      UUID tenant, UUID payment, Pageable page);

  @Query(
      value =
          """
          SELECT DISTINCT p.id FROM finance.finance_payment p JOIN finance.collection_payment_match m
            ON m.tenant_id=p.tenant_id AND m.payment_id=p.id
          WHERE p.tenant_id=:tenant AND p.id>:after AND p.status='VOIDED' AND m.payment_invalidated_at IS NULL
          ORDER BY p.id
          """,
      nativeQuery = true)
  List<UUID> voidPaymentsToObserve(UUID tenant, UUID after, Pageable page);

  interface Balance {
    UUID getId();

    BigDecimal getAmount();
  }
}
