package com.fabricmanagement.finance.collection.infra.repository;

import com.fabricmanagement.finance.collection.domain.CollectionChequeApplication;
import com.fabricmanagement.finance.collection.infra.repository.CollectionPaymentMatchRepository.Balance;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CollectionChequeApplicationRepository
    extends JpaRepository<CollectionChequeApplication, UUID> {
  List<CollectionChequeApplication> findByTenantIdAndChequeId(UUID tenant, UUID cheque);

  /**
   * What instruments cover per obligation. A cheque not yet cleared covers its whole application. A
   * cleared one covers what its clearing matches still hold: an application never changes, but when
   * part of the cleared money is refunded the match is sliced, and the refunded part no longer
   * covers anything. Counting the application would show that part as delivered and refuse a new
   * cheque for it.
   */
  @Query(
      value =
          """
          SELECT a.obligation_id AS id,
            sum(CASE WHEN c.state='CLEARED' THEN LEAST(a.amount, COALESCE(h.held,0)) ELSE a.amount END) AS amount
          FROM finance.collection_cheque_application a
          JOIN finance.collection_cheque c ON c.tenant_id=a.tenant_id AND c.id=a.cheque_id
          LEFT JOIN LATERAL (
            SELECT sum(m.amount) AS held FROM finance.collection_payment_match m
            WHERE m.tenant_id=a.tenant_id AND m.cheque_id=a.cheque_id AND m.obligation_id=a.obligation_id
              AND m.reversed_at IS NULL AND m.is_active
          ) h ON true
          WHERE a.tenant_id=:tenant AND a.obligation_id IN (:ids) AND c.state IN ('RECEIVED','PRESENTED','CLEARED')
            AND a.is_active AND c.is_active GROUP BY a.obligation_id
          """,
      nativeQuery = true)
  List<Balance> received(UUID tenant, List<UUID> ids);

  /** What received-but-not-yet-cleared cheques already cover per obligation. */
  @Query(
      value =
          """
          SELECT a.obligation_id AS id, sum(a.amount) AS amount FROM finance.collection_cheque_application a
          JOIN finance.collection_cheque c ON c.tenant_id=a.tenant_id AND c.id=a.cheque_id
          WHERE a.tenant_id=:tenant AND a.obligation_id IN (:ids) AND c.state IN ('RECEIVED','PRESENTED')
            AND a.is_active AND c.is_active GROUP BY a.obligation_id
          """,
      nativeQuery = true)
  List<Balance> pending(UUID tenant, List<UUID> ids);

  @Query(
      value =
          """
          SELECT a.cheque_id FROM finance.collection_cheque_application a
          WHERE a.tenant_id=:tenant AND a.obligation_id=:obligation ORDER BY a.cheque_id
          """,
      countQuery =
          "SELECT count(*) FROM finance.collection_cheque_application WHERE tenant_id=:tenant AND"
              + " obligation_id=:obligation",
      nativeQuery = true)
  Page<UUID> chequeIds(UUID tenant, UUID obligation, Pageable page);
}
