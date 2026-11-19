package com.fabricmanagement.finance.collection.infra.repository;

import com.fabricmanagement.finance.collection.domain.CollectionChequeApplication;
import com.fabricmanagement.finance.collection.infra.repository.CollectionPaymentMatchRepository.Balance;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Cheque applications are evidence (PAYMENT-PLAN-9 K2): a reversed row stays. Current coverage,
 * clearing, access and versions read the active (unreversed) rows; the full list is history. A
 * cheque may hold several active rows for one obligation, so readers sum amounts per obligation and
 * check each obligation once.
 */
public interface CollectionChequeApplicationRepository
    extends JpaRepository<CollectionChequeApplication, UUID> {
  java.util.Optional<CollectionChequeApplication> findByTenantIdAndId(UUID tenant, UUID id);

  /** Every application of the cheque, reversed ones included: its history. */
  List<CollectionChequeApplication> findByTenantIdAndChequeId(UUID tenant, UUID cheque);

  /** The cheque's applications that are not reversed: what it currently covers. */
  @Query(
      "select a from CollectionChequeApplication a where a.tenantId=:tenant and a.chequeId=:cheque"
          + " and a.reversedAt is null and a.isActive=true order by a.createdAt, a.id")
  List<CollectionChequeApplication> active(UUID tenant, UUID cheque);

  /** The active applications of several cheques. */
  @Query(
      "select a from CollectionChequeApplication a where a.tenantId=:tenant and a.chequeId in"
          + " :cheques and a.reversedAt is null and a.isActive=true order by a.createdAt, a.id")
  List<CollectionChequeApplication> activeOf(UUID tenant, java.util.Collection<UUID> cheques);

  /**
   * What instruments cover per obligation. A cheque not yet cleared covers its whole application. A
   * cleared one covers what its clearing matches still hold: an application never changes, but when
   * part of the cleared money is refunded the match is sliced, and the refunded part no longer
   * covers anything. Counting the application would show that part as delivered and refuse a new
   * cheque for it. Active applications are summed per cheque and obligation before the cap: capping
   * each row on its own would let two rows of one pair both claim the same held amount.
   */
  @Query(
      value =
          """
          SELECT p.obligation_id AS id,
            sum(CASE WHEN p.state='CLEARED' THEN LEAST(p.applied, COALESCE(h.held,0)) ELSE p.applied END) AS amount
          FROM (
            SELECT a.tenant_id, a.cheque_id, a.obligation_id, c.state, sum(a.amount) AS applied
            FROM finance.collection_cheque_application a
            JOIN finance.collection_cheque c ON c.tenant_id=a.tenant_id AND c.id=a.cheque_id
            WHERE a.tenant_id=:tenant AND a.obligation_id IN (:ids) AND c.state IN ('RECEIVED','PRESENTED','CLEARED')
              AND a.reversed_at IS NULL AND a.is_active AND c.is_active
            GROUP BY a.tenant_id, a.cheque_id, a.obligation_id, c.state
          ) p
          LEFT JOIN LATERAL (
            SELECT sum(m.amount) AS held FROM finance.collection_payment_match m
            WHERE m.tenant_id=p.tenant_id AND m.cheque_id=p.cheque_id AND m.obligation_id=p.obligation_id
              AND m.reversed_at IS NULL AND m.is_active
          ) h ON true
          GROUP BY p.obligation_id
          """,
      nativeQuery = true)
  List<Balance> received(UUID tenant, List<UUID> ids);

  /** What received-but-not-yet-cleared cheques already cover per obligation (active rows only). */
  @Query(
      value =
          """
          SELECT a.obligation_id AS id, sum(a.amount) AS amount FROM finance.collection_cheque_application a
          JOIN finance.collection_cheque c ON c.tenant_id=a.tenant_id AND c.id=a.cheque_id
          WHERE a.tenant_id=:tenant AND a.obligation_id IN (:ids) AND c.state IN ('RECEIVED','PRESENTED')
            AND a.reversed_at IS NULL AND a.is_active AND c.is_active GROUP BY a.obligation_id
          """,
      nativeQuery = true)
  List<Balance> pending(UUID tenant, List<UUID> ids);

  /**
   * The cheques that currently cover the obligation, each once (PAYMENT-PLAN-9 K5). A cheque whose
   * applications to it were all reversed is reached through its own history instead.
   */
  @Query(
      value =
          """
          SELECT DISTINCT a.cheque_id FROM finance.collection_cheque_application a
          WHERE a.tenant_id=:tenant AND a.obligation_id=:obligation AND a.reversed_at IS NULL
            AND a.is_active ORDER BY a.cheque_id
          """,
      countQuery =
          "SELECT count(DISTINCT cheque_id) FROM finance.collection_cheque_application WHERE"
              + " tenant_id=:tenant AND obligation_id=:obligation AND reversed_at IS NULL AND"
              + " is_active",
      nativeQuery = true)
  Page<UUID> chequeIds(UUID tenant, UUID obligation, Pageable page);
}
